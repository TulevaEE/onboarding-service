package ee.tuleva.onboarding.investment.report.publishing;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient.UploadResult;
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

    var navDateErrors = validateNavDates(month);
    if (!navDateErrors.isEmpty()) {
      log.warn("Pre-publish validation failed: {}", navDateErrors);
      return abortedWithoutPublishing(navDateErrors);
    }

    logQuantityWarnings(month);

    var generation = generateReports(month);
    if (!generation.allocationErrors().isEmpty()) {
      log.warn("Allocation validation failed, aborting publish: {}", generation.allocationErrors());
      return abortedWithoutPublishing(generation.allocationErrors());
    }
    if (!generation.errors().isEmpty()) {
      log.warn("Report generation failed, aborting before any upload: {}", generation.errors());
      return abortedWithoutPublishing(generation.errors());
    }
    var pdfs = generation.pdfs();

    var uploadErrors = new ArrayList<String>();
    var uploads = uploadAllReports(month, pdfs, uploadErrors);
    if (!uploadErrors.isEmpty()) {
      log.warn("Uploads failed, no fund pages changed, website unchanged: {}", uploadErrors);
      return abortedWithoutPublishing(uploadErrors);
    }

    var repointErrors = new ArrayList<String>();
    var publishedUrls = repointFundPages(uploads, repointErrors);
    if (!repointErrors.isEmpty()) {
      log.error(
          "Partial publish, some fund pages updated and some not: updated={}, errors={}",
          publishedUrls.keySet(),
          repointErrors);
      alertPartialPublish(month, publishedUrls.keySet(), repointErrors);
      return new InvestmentReportPublishingResult(publishedUrls, false, repointErrors);
    }

    var emailErrors = new ArrayList<String>();
    var emailSent = emailReports(month, pdfs, emailErrors);
    log.info(
        "Investment report publishing completed: publishedFunds={}, emailSent={}",
        publishedUrls.size(),
        emailSent);
    return new InvestmentReportPublishingResult(publishedUrls, emailSent, emailErrors);
  }

  private static InvestmentReportPublishingResult abortedWithoutPublishing(List<String> errors) {
    return new InvestmentReportPublishingResult(Map.of(), false, errors);
  }

  private void logQuantityWarnings(YearMonth month) {
    for (var mapping : FundReportMapping.all()) {
      var quantityWarnings = dataService.validateQuantities(mapping.fund(), month);
      if (!quantityWarnings.isEmpty()) {
        log.warn(
            "Quantity mismatch warnings for {}: {}", mapping.fund().getCode(), quantityWarnings);
      }
    }
  }

  private ReportGeneration generateReports(YearMonth month) {
    var pdfs = new LinkedHashMap<FundReportMapping, byte[]>();
    var errors = new ArrayList<String>();
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
        log.error("PDF generation failed for {}", mapping.fund().getCode(), e);
        errors.add(
            "PDF generation failed for %s: %s".formatted(mapping.fund().getCode(), e.getMessage()));
      }
    }
    return new ReportGeneration(pdfs, errors, allocationErrors);
  }

  private Map<FundReportMapping, UploadResult> uploadAllReports(
      YearMonth month, Map<FundReportMapping, byte[]> pdfs, List<String> errors) {
    var uploads = new LinkedHashMap<FundReportMapping, UploadResult>();
    for (var entry : pdfs.entrySet()) {
      var mapping = entry.getKey();
      try {
        uploads.put(
            mapping, wordPressClient.upload(mapping.buildPdfFilename(month), entry.getValue()));
      } catch (Exception e) {
        log.error("WordPress upload failed for {}", mapping.fund().getCode(), e);
        errors.add(
            "WordPress upload failed for %s: %s"
                .formatted(mapping.fund().getCode(), e.getMessage()));
      }
    }
    return uploads;
  }

  private Map<String, String> repointFundPages(
      Map<FundReportMapping, UploadResult> uploads, List<String> errors) {
    var publishedUrls = new LinkedHashMap<String, String>();
    for (var entry : uploads.entrySet()) {
      var mapping = entry.getKey();
      var upload = entry.getValue();
      try {
        wordPressClient.updateAcfReportField(mapping.pageSlug(), upload.attachmentId());
        publishedUrls.put(mapping.fund().getCode(), upload.sourceUrl());
      } catch (Exception e) {
        log.error("WordPress page update failed for {}", mapping.fund().getCode(), e);
        errors.add(
            "WordPress page update failed for %s: %s"
                .formatted(mapping.fund().getCode(), e.getMessage()));
      }
    }
    return publishedUrls;
  }

  private boolean emailReports(
      YearMonth month, Map<FundReportMapping, byte[]> pdfs, List<String> errors) {
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
      message.setAttachments(emailAttachments(month, pdfs));
      return emailService.sendSystemEmail(message);
    } catch (Exception e) {
      log.error("Email send failed", e);
      errors.add("Email send failed: " + e.getMessage());
      return false;
    }
  }

  private static List<MessageContent> emailAttachments(
      YearMonth month, Map<FundReportMapping, byte[]> pdfs) {
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
    return attachments;
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

    var allFundCodes = FundReportMapping.all().stream().map(m -> m.fund().getCode()).toList();
    var missingFunds = allFundCodes.stream().filter(code -> !navDates.containsKey(code)).toList();
    if (!missingFunds.isEmpty()) {
      errors.add("No published NAV data for funds: %s (month=%s)".formatted(missingFunds, month));
      return errors;
    }

    var distinctDates = new LinkedHashSet<>(navDates.values());
    if (distinctDates.size() > 1) {
      errors.add("Inconsistent NAV dates across funds: %s".formatted(navDates));
      return errors;
    }

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

  private record ReportGeneration(
      Map<FundReportMapping, byte[]> pdfs, List<String> errors, List<String> allocationErrors) {}
}
