package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.github.GitHubPrClient;
import ee.tuleva.onboarding.investment.report.publishing.gmail.GmailDraftClient;
import ee.tuleva.onboarding.investment.report.publishing.gmail.GmailDraftClient.PdfAttachment;
import ee.tuleva.onboarding.investment.report.publishing.gmail.GmailProperties;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
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

  private final InvestmentReportDataService dataService;
  private final InvestmentReportPdfGenerator pdfGenerator;
  private final WordPressMediaClient wordPressClient;
  private final GitHubPrClient gitHubPrClient;
  private final GmailDraftClient gmailDraftClient;
  private final GmailProperties gmailProperties;

  public InvestmentReportPublishingResult publish(YearMonth month) {
    log.info("Starting investment report publishing for month={}", month);

    var errors = new ArrayList<String>();
    var pdfs = new LinkedHashMap<FundReportMapping, byte[]>();
    var wpUrls = new LinkedHashMap<String, String>();
    String prUrl = null;
    String draftId = null;

    // Step 1: Generate PDFs for all funds
    for (var mapping : FundReportMapping.all()) {
      try {
        var context = dataService.getReportData(mapping.fund(), month);
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
}
