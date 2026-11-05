package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.UploadFailed.MayBeStored;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.UploadFailed.NothingStored;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient.UploadResult;
import java.time.Clock;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
@RequiredArgsConstructor
class ReportUploads {

  private static final String NOTHING_STORED =
      "WordPress upload did not reach WordPress or was refused, so nothing was stored and nothing"
          + " needs deleting; the fund page still links the report to keep: fund=%s, keep=%s,"
          + " cause=%s";
  private static final String MAY_BE_STORED =
      "WordPress upload failed after it may have reached WordPress; keep the report the fund page"
          + " links, and delete only an attachment uploaded at uploadedAt that no fund page links:"
          + " fund=%s, keep=%s, uploadedAt=%s, cause=%s";
  private static final String NO_REPORT_LINKED = "no report linked";

  private final WordPressMediaClient wordPressClient;
  private final Clock clock;

  Map<FundReportMapping, UploadResult> uploadAll(
      YearMonth month,
      Map<FundReportMapping, byte[]> pdfs,
      Map<FundReportMapping, String> linkedBeforeUpload,
      List<String> errors) {
    var uploads = new LinkedHashMap<FundReportMapping, UploadResult>();
    pdfs.forEach(
        (mapping, pdf) -> {
          var fundCode = mapping.fund().getCode();
          var keep = linkedBeforeUpload.getOrDefault(mapping, NO_REPORT_LINKED);
          try {
            uploads.put(mapping, wordPressClient.upload(mapping.buildPdfFilename(month), pdf));
          } catch (MayBeStored mayBeStored) {
            log.error("Report upload may have reached WordPress: fund={}", fundCode, mayBeStored);
            errors.add(
                MAY_BE_STORED.formatted(
                    fundCode, keep, clock.instant(), mayBeStored.failedBecause()));
          } catch (NothingStored nothingStored) {
            log.error("Report upload stored nothing: fund={}", fundCode, nothingStored);
            errors.add(NOTHING_STORED.formatted(fundCode, keep, nothingStored.failedBecause()));
          } catch (RuntimeException beforeAnyRequest) {
            log.error("Report upload did not start: fund={}", fundCode, beforeAnyRequest);
            errors.add(
                NOTHING_STORED.formatted(
                    fundCode, keep, beforeAnyRequest.getClass().getSimpleName()));
          }
        });
    return uploads;
  }
}
