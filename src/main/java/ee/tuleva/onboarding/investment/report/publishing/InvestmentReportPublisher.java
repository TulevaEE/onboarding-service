package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient.PageUpdateOutcomeUnknown;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient.UploadResult;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
@EnableConfigurationProperties(InvestmentReportPublishingProperties.class)
@RequiredArgsConstructor
public class InvestmentReportPublisher {

  private static final int MIN_END_OF_MONTH_DAY = 25;
  private static final BigDecimal ALLOCATION_LOWER = new BigDecimal("0.985");
  private static final BigDecimal ALLOCATION_UPPER = new BigDecimal("1.015");
  private static final String LOST_THE_LOCK_BEFORE_THE_REPOINT =
      "The publish lost its lock, so this fund page was not repointed: fund=%s";
  private static final String LOST_THE_LOCK_BEFORE_THE_EMAIL =
      "The publish lost its lock after repointing the fund pages, so the reports were not"
          + " e-mailed: month=%s";
  private static final String UPDATE_NOT_LINKED =
      "WordPress page update failed, so the uploaded report was not linked; link it or delete it"
          + " from the media library: fund=%s, file=%s, error=%s";
  private static final String UPDATE_OUTCOME_UNKNOWN =
      "WordPress page update failed and the page could not be read back, so whether it links"
          + " the uploaded report is unknown; check the fund page before deleting anything:"
          + " fund=%s, file=%s, error=%s";
  private static final String UPLOAD_NOT_LINKED =
      "Uploaded report was not linked; link it or delete it from the media library:"
          + " fund=%s, file=%s";

  private final InvestmentReportDataService dataService;
  private final InvestmentReportPdfGenerator pdfGenerator;
  private final WordPressMediaClient wordPressClient;
  private final NewerReportCheck newerReportCheck;
  private final InvestmentReportMailer mailer;
  private final InvestmentReportPublishingLock publishingLock;
  private final InvestmentReportPublishingProperties properties;

  public InvestmentReportPublishingResult publish(
      final YearMonth month, final Set<String> unrecognisedFilesToReplace) {
    return publishingLock.whileNoOtherPublishRuns(
        lockStillHeld ->
            publishWhileNoOtherPublishRuns(month, unrecognisedFilesToReplace, lockStillHeld),
        refusal -> anotherPublishIsRunning(month, refusal));
  }

  private static InvestmentReportPublishingResult anotherPublishIsRunning(
      YearMonth month, String refusal) {
    var error = "%s, month=%s".formatted(refusal, month);
    log.warn("Nothing published: {}", error);
    return abortedWithoutPublishing(List.of(error));
  }

  private InvestmentReportPublishingResult publishWhileNoOtherPublishRuns(
      YearMonth month, Set<String> unrecognisedFilesToReplace, BooleanSupplier lockStillHeld) {
    log.info(
        "Starting investment report publishing: month={}, unrecognisedFilesToReplace={}",
        month,
        unrecognisedFilesToReplace);

    var navDateErrors = validateNavDates(month);
    if (!navDateErrors.isEmpty()) {
      log.warn("Pre-publish validation failed: {}", navDateErrors);
      return abortedWithoutPublishing(navDateErrors);
    }

    var refusals =
        newerReportCheck.reasonsNotToPublish(
            configuredReports(), month, unrecognisedFilesToReplace);
    if (!refusals.isEmpty()) {
      log.warn("Fund page check refused the publish: month={}, errors={}", month, refusals);
      return abortedWithoutPublishing(refusals);
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
      uploads.forEach(
          (mapping, upload) ->
              uploadErrors.add(
                  UPLOAD_NOT_LINKED.formatted(mapping.fund().getCode(), upload.sourceUrl())));
      log.warn("Uploads failed, no fund pages changed, website unchanged: {}", uploadErrors);
      mailer.alertPartialPublish(month, Map.of(), uploadErrors);
      return abortedWithoutPublishing(uploadErrors);
    }

    var repointErrors = new ArrayList<String>();
    var publishedUrls =
        repointFundPages(month, unrecognisedFilesToReplace, lockStillHeld, uploads, repointErrors);
    if (!repointErrors.isEmpty()) {
      log.error(
          "Partial publish, some fund pages updated and some not: updated={}, errors={}",
          publishedUrls.keySet(),
          repointErrors);
      mailer.alertPartialPublish(month, publishedUrls, repointErrors);
      return new InvestmentReportPublishingResult(publishedUrls, false, repointErrors);
    }
    if (!lockStillHeld.getAsBoolean()) {
      var lostTheLock = List.of(LOST_THE_LOCK_BEFORE_THE_EMAIL.formatted(month));
      log.error("Pages repointed but the reports not e-mailed: {}", lostTheLock);
      mailer.alertPartialPublish(month, publishedUrls, lostTheLock);
      return new InvestmentReportPublishingResult(publishedUrls, false, lostTheLock);
    }

    var emailErrors = new ArrayList<String>();
    var emailSent = mailer.emailReports(month, pdfs, emailErrors);
    log.info(
        "Investment report publishing completed: publishedFunds={}, emailSent={}",
        publishedUrls.size(),
        emailSent);
    return new InvestmentReportPublishingResult(publishedUrls, emailSent, emailErrors);
  }

  private static InvestmentReportPublishingResult abortedWithoutPublishing(List<String> errors) {
    return new InvestmentReportPublishingResult(Map.of(), false, errors);
  }

  private List<FundReportMapping> configuredReports() {
    return properties.funds().stream().map(FundReportMapping::forFund).toList();
  }

  private void logQuantityWarnings(YearMonth month) {
    for (var mapping : configuredReports()) {
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
    for (var mapping : configuredReports()) {
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
      var filename = mapping.buildPdfFilename(month);
      try {
        uploads.put(mapping, wordPressClient.upload(filename, entry.getValue()));
      } catch (Exception e) {
        log.error("WordPress upload failed for {}", mapping.fund().getCode(), e);
        errors.add(
            ("WordPress upload failed but may have stored the file anyway, under this name's"
                    + " slug and possibly a -N suffix; delete it from the media library if it is"
                    + " there: fund=%s, file=%s, error=%s")
                .formatted(mapping.fund().getCode(), filename, e.getMessage()));
      }
    }
    return uploads;
  }

  private Map<String, String> repointFundPages(
      YearMonth month,
      Set<String> unrecognisedFilesToReplace,
      BooleanSupplier lockStillHeld,
      Map<FundReportMapping, UploadResult> uploads,
      List<String> errors) {
    var publishedUrls = new LinkedHashMap<String, String>();
    for (var entry : uploads.entrySet()) {
      var mapping = entry.getKey();
      var upload = entry.getValue();
      var refusedSinceTheCheck =
          lockStillHeld.getAsBoolean()
              ? newerReportCheck.reasonsNotToRepoint(mapping, month, unrecognisedFilesToReplace)
              : List.of(LOST_THE_LOCK_BEFORE_THE_REPOINT.formatted(mapping.fund().getCode()));
      if (!refusedSinceTheCheck.isEmpty()) {
        errors.addAll(refusedSinceTheCheck);
        errors.add(UPLOAD_NOT_LINKED.formatted(mapping.fund().getCode(), upload.sourceUrl()));
        continue;
      }
      try {
        wordPressClient.updateAcfReportField(
            mapping.pageSlug(), upload.attachmentId(), lockStillHeld);
        publishedUrls.put(mapping.fund().getCode(), upload.sourceUrl());
      } catch (Exception e) {
        log.error("WordPress page update failed for {}", mapping.fund().getCode(), e);
        errors.add(
            (e instanceof PageUpdateOutcomeUnknown ? UPDATE_OUTCOME_UNKNOWN : UPDATE_NOT_LINKED)
                .formatted(mapping.fund().getCode(), upload.sourceUrl(), e.getMessage()));
      }
    }
    return publishedUrls;
  }

  private List<String> validateNavDates(YearMonth month) {
    var navDates = dataService.findNavDates(properties.funds(), month);
    var errors = new ArrayList<String>();

    var allFundCodes = properties.funds().stream().map(TulevaFund::getCode).toList();
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

  private @Nullable String validateAllocation(String fundCode, InvestmentReportContext context) {
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
