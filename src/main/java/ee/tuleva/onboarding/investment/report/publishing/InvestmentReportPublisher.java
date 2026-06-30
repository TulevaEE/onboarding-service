package ee.tuleva.onboarding.investment.report.publishing;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class InvestmentReportPublisher {

  private static final int MIN_END_OF_MONTH_DAY = 25;
  private static final BigDecimal ALLOCATION_LOWER = new BigDecimal("0.985");
  private static final BigDecimal ALLOCATION_UPPER = new BigDecimal("1.015");

  private final InvestmentReportDataService dataService;
  private final InvestmentReportPdfGenerator pdfGenerator;
  private final WordPressMediaClient wordPressClient;
  private final EmailService emailService;

  public InvestmentReportPublishingResult publish(YearMonth month) {
    log.info("Starting investment report publishing for month={}", month);

    // Pre-publish validation: NAV dates
    var navDateErrors = validateNavDates(month);
    if (!navDateErrors.isEmpty()) {
      log.warn("Pre-publish validation failed: {}", navDateErrors);
      return new InvestmentReportPublishingResult(Map.of(), false, navDateErrors);
    }

    // Pre-publish validation: quantity reconciliation (warning only)
    for (var mapping : FundReportMapping.all()) {
      var quantityWarnings = dataService.validateQuantities(mapping.fund(), month);
      if (!quantityWarnings.isEmpty()) {
        log.warn(
            "Quantity mismatch warnings for {}: {}", mapping.fund().getCode(), quantityWarnings);
      }
    }

    var errors = new ArrayList<String>();
    var pdfs = new LinkedHashMap<FundReportMapping, byte[]>();
    var wpUrls = new LinkedHashMap<String, String>();
    boolean emailSent = false;

    // Step 1: Generate PDFs for all funds and validate allocation
    var allocationErrors = new ArrayList<String>();
    for (var mapping : FundReportMapping.all()) {
      try {
        var context = dataService.getReportData(mapping.fund(), month);
        var allocationError = validateAllocation(mapping.fund().getCode(), context);
        if (allocationError != null) {
          allocationErrors.add(allocationError);
          continue;
        }
        var pdfBytes = pdfGenerator.generatePdf(context);
        pdfs.put(mapping, pdfBytes);
        log.info("Generated PDF: fund={}, size={}bytes", mapping.fund().getCode(), pdfBytes.length);
      } catch (Exception e) {
        var msg =
            "PDF generation failed for %s: %s".formatted(mapping.fund().getCode(), e.getMessage());
        log.error(msg, e);
        errors.add(msg);
      }
    }

    if (!allocationErrors.isEmpty()) {
      log.warn("Allocation validation failed, aborting publish: {}", allocationErrors);
      return new InvestmentReportPublishingResult(Map.of(), false, allocationErrors);
    }

    // If any fund's report could not be produced, publish nothing: updating only some fund pages
    // would leave the site showing a new report for some funds and last month's for others.
    if (!errors.isEmpty()) {
      log.warn("PDF generation failed, aborting before any upload: errors={}", errors);
      return new InvestmentReportPublishingResult(Map.of(), false, errors);
    }

    // Step 2a: Upload every PDF first. No fund page is repointed until all uploads succeed, so an
    // upload failure cannot leave the live website half-updated.
    var uploads = new LinkedHashMap<FundReportMapping, WordPressMediaClient.UploadResult>();
    for (var entry : pdfs.entrySet()) {
      var mapping = entry.getKey();
      try {
        uploads.put(
            mapping, wordPressClient.upload(mapping.buildPdfFilename(month), entry.getValue()));
      } catch (Exception e) {
        var msg =
            "WordPress upload failed for %s: %s"
                .formatted(mapping.fund().getCode(), e.getMessage());
        log.error(msg, e);
        errors.add(msg);
      }
    }
    if (!errors.isEmpty()) {
      log.warn("Upload errors, no fund pages updated, website unchanged: errors={}", errors);
      return new InvestmentReportPublishingResult(Map.of(), false, errors);
    }

    // Step 2b: Point each fund page at its uploaded report. All uploads succeeded, so a failure
    // here is a genuine partial publish that needs manual attention.
    for (var entry : uploads.entrySet()) {
      var mapping = entry.getKey();
      var upload = entry.getValue();
      try {
        wordPressClient.updateAcfReportField(mapping.pageSlug(), upload.attachmentId());
        wpUrls.put(mapping.fund().getCode(), upload.sourceUrl());
      } catch (Exception e) {
        var msg =
            "WordPress page update failed for %s: %s"
                .formatted(mapping.fund().getCode(), e.getMessage());
        log.error(msg, e);
        errors.add(msg);
      }
    }
    if (!errors.isEmpty()) {
      log.error(
          "Partial publish: some fund pages updated, some not: updated={}, errors={}",
          wpUrls.keySet(),
          errors);
      alertPartialPublish(month, wpUrls.keySet(), errors);
      return new InvestmentReportPublishingResult(wpUrls, false, errors);
    }

    // Step 3: Send email with PDF attachments (only funds included in email)
    try {
      var message = new MandrillMessage();
      message.setFromEmail("funds@tuleva.ee");
      message.setFromName("Tuleva");
      message.setSubject("Tuleva investeeringute aruanded " + month);
      message.setHtml(
          "<p>Tere,</p>"
              + "<p>Manuses on Tuleva pensionifondide investeeringute aruanded.</p>"
              + "<p>See on automaatne kiri.</p>"
              + "<p>Parimat,<br>Tuleva robot</p>");

      var recipient = new Recipient();
      recipient.setEmail("funds@tuleva.ee");
      recipient.setType(TO);
      message.setTo(List.of(recipient));

      var attachments = new ArrayList<MessageContent>();
      for (var entry : pdfs.entrySet()) {
        if (entry.getKey().includeInEmail()) {
          var attachment = new MessageContent();
          attachment.setName(entry.getKey().buildPdfFilename(month));
          attachment.setType("application/pdf");
          attachment.setContent(Base64.getEncoder().encodeToString(entry.getValue()));
          attachments.add(attachment);
        }
      }
      message.setAttachments(attachments);

      emailSent = emailService.sendSystemEmail(message);
    } catch (Exception e) {
      var msg = "Email send failed: " + e.getMessage();
      log.error(msg, e);
      errors.add(msg);
    }

    var result = new InvestmentReportPublishingResult(wpUrls, emailSent, errors);
    log.info(
        "Investment report publishing completed: wpUrls={}, emailSent={}, errors={}",
        wpUrls.size(),
        emailSent,
        errors.size());
    return result;
  }

  private void alertPartialPublish(
      YearMonth month, Set<String> publishedFunds, List<String> errors) {
    try {
      var message = new MandrillMessage();
      message.setFromEmail("funds@tuleva.ee");
      message.setFromName("Tuleva");
      message.setSubject("Investeeringute aruannete avaldamine jäi pooleli: " + month);
      message.setText(
          ("Investment report publish for %s left the website in a partial state.%n"
                  + "Fund pages updated: %s%nErrors: %s%n"
                  + "Manual attention needed to make all fund pages consistent.")
              .formatted(month, publishedFunds, errors));
      var recipient = new Recipient();
      recipient.setEmail("funds@tuleva.ee");
      recipient.setType(TO);
      message.setTo(List.of(recipient));
      emailService.sendSystemEmail(message);
    } catch (Exception e) {
      log.error("Failed to send partial-publish alert: {}", e.getMessage(), e);
    }
  }

  private List<String> validateNavDates(YearMonth month) {
    var navDates = dataService.findNavDatesForAllFunds(month);
    var errors = new ArrayList<String>();

    // Check all funds have published NAV data
    var allFundCodes = FundReportMapping.all().stream().map(m -> m.fund().getCode()).toList();
    var missingFunds = allFundCodes.stream().filter(code -> !navDates.containsKey(code)).toList();
    if (!missingFunds.isEmpty()) {
      errors.add("No published NAV data for funds: %s (month=%s)".formatted(missingFunds, month));
      return errors;
    }

    // Check all funds have the same NAV date
    var distinctDates = new LinkedHashSet<>(navDates.values());
    if (distinctDates.size() > 1) {
      errors.add("Inconsistent NAV dates across funds: %s".formatted(navDates));
      return errors;
    }

    // Check NAV date is end-of-month (day >= 25)
    var navDate = distinctDates.iterator().next();
    if (navDate.getDayOfMonth() < MIN_END_OF_MONTH_DAY) {
      errors.add(
          "NAV date %s is mid-month (day %d, expected >= %d)"
              .formatted(navDate, navDate.getDayOfMonth(), MIN_END_OF_MONTH_DAY));
    }

    return errors;
  }

  private String validateAllocation(String fundCode, InvestmentReportContext context) {
    var pct = context.totalAssetsNavPercent();
    if (pct.compareTo(ALLOCATION_LOWER) < 0 || pct.compareTo(ALLOCATION_UPPER) > 0) {
      return "%s total asset allocation is %.2f%% (expected ~100%%, tolerance ±1.5%%)"
          .formatted(fundCode, pct.movePointRight(2));
    }
    return null;
  }
}
