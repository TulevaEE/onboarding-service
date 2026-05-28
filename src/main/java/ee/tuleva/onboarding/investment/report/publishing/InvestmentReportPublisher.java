package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.github.GitHubPrClient;
import ee.tuleva.onboarding.investment.report.publishing.gmail.GmailDraftClient;
import ee.tuleva.onboarding.investment.report.publishing.gmail.GmailDraftClient.PdfAttachment;
import ee.tuleva.onboarding.investment.report.publishing.gmail.GmailProperties;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
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
  private final GitHubPrClient gitHubPrClient;
  private final GmailDraftClient gmailDraftClient;
  private final GmailProperties gmailProperties;

  public InvestmentReportPublishingResult publish(YearMonth month) {
    log.info("Starting investment report publishing for month={}", month);

    // Pre-publish validation: NAV dates
    var navDateErrors = validateNavDates(month);
    if (!navDateErrors.isEmpty()) {
      log.warn("Pre-publish validation failed: {}", navDateErrors);
      return new InvestmentReportPublishingResult(Map.of(), null, null, navDateErrors);
    }

    var errors = new ArrayList<String>();
    var pdfs = new LinkedHashMap<FundReportMapping, byte[]>();
    var wpUrls = new LinkedHashMap<String, String>();
    String prUrl = null;
    String draftId = null;

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
      return new InvestmentReportPublishingResult(Map.of(), null, null, allocationErrors);
    }

    // Step 2: Upload all PDFs to WordPress
    for (var entry : pdfs.entrySet()) {
      try {
        var filename = entry.getKey().buildPdfFilename(month);
        var sourceUrl = wordPressClient.upload(filename, entry.getValue());
        wpUrls.put(entry.getKey().fund().getCode(), sourceUrl);
      } catch (Exception e) {
        var msg =
            "WordPress upload failed for %s: %s"
                .formatted(entry.getKey().fund().getCode(), e.getMessage());
        log.error(msg, e);
        errors.add(msg);
      }
    }

    // Step 3: Create GitHub PR (only funds included in PR with successful WP uploads)
    try {
      var prMappings = new LinkedHashMap<FundReportMapping, String>();
      for (var entry : pdfs.entrySet()) {
        var mapping = entry.getKey();
        if (mapping.includeInPr() && wpUrls.containsKey(mapping.fund().getCode())) {
          prMappings.put(mapping, wpUrls.get(mapping.fund().getCode()));
        }
      }
      if (!prMappings.isEmpty()) {
        prUrl = gitHubPrClient.createReportPr(prMappings, month);
      }
    } catch (Exception e) {
      var msg = "GitHub PR creation failed: " + e.getMessage();
      log.error(msg, e);
      errors.add(msg);
    }

    // Step 4: Create Gmail draft (only funds included in email)
    try {
      var attachments = new ArrayList<PdfAttachment>();
      for (var entry : pdfs.entrySet()) {
        if (entry.getKey().includeInEmail()) {
          attachments.add(
              new PdfAttachment(entry.getKey().buildPdfFilename(month), entry.getValue()));
        }
      }
      if (!attachments.isEmpty()) {
        var signature = gmailDraftClient.fetchSignature();
        var htmlBody =
            "Tere<br><br>"
                + "Saadan Tuleva pensionifondide investeeringute aruanded.<br><br>"
                + signature;
        draftId =
            gmailDraftClient.createDraft(
                gmailProperties.to(),
                gmailProperties.cc(),
                "Tuleva investeeringute aruanded",
                htmlBody,
                attachments);
      }
    } catch (Exception e) {
      var msg = "Gmail draft creation failed: " + e.getMessage();
      log.error(msg, e);
      errors.add(msg);
    }

    var result = new InvestmentReportPublishingResult(wpUrls, prUrl, draftId, errors);
    log.info(
        "Investment report publishing completed: wpUrls={}, prUrl={}, draftId={}, errors={}",
        wpUrls.size(),
        prUrl != null ? "created" : "skipped",
        draftId != null ? "created" : "skipped",
        errors.size());
    return result;
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

  // TODO: validate instrument quantities match between NAV report and transaction
  //       registry settled positions (safety net for late-arriving settlement data)
}
