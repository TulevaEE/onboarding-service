package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.UploadFailed.MayBeStored;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.UploadFailed.NothingStored;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.UploadFailed.Stored;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient.UploadResult;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
@RequiredArgsConstructor
class ReportUploads {

  private static final DateTimeFormatter AS_THE_MEDIA_LIBRARY_SHOWS_IT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Tallinn"));
  private static final String STORED_BUT_UNUSABLE =
      "WordPress stored the upload but its answer could not be used, so it is not linked; link or"
          + " delete this attachment in the media library: fund=%s, attachmentId=%d, cause=%s";
  private static final String MAY_BE_STORED =
      "WordPress upload failed but may have stored the report; in the media library delete only an"
          + " attachment uploaded at uploadedAt that no fund page links: fund=%s, uploadedAt=%s,"
          + " cause=%s";
  private static final String MAY_BE_STORED_BESIDE_THE_LINKED_REPORT =
      "WordPress upload failed but may have stored the report; in the media library delete only an"
          + " attachment uploaded at uploadedAt that no fund page links, and keep linkedReport,"
          + " which the fund page links: fund=%s, uploadedAt=%s, linkedReport=%s, cause=%s";
  private static final String NOTHING_STORED =
      "WordPress upload did not reach WordPress, so nothing was stored and nothing needs"
          + " deleting: fund=%s, cause=%s";
  private static final String NOTHING_STORED_BESIDE_THE_LINKED_REPORT =
      "WordPress upload did not reach WordPress, so nothing was stored and nothing needs"
          + " deleting; the fund page still links linkedReport: fund=%s, linkedReport=%s, cause=%s";

  private final WordPressMediaClient wordPressClient;
  private final Clock clock;

  Map<FundReportMapping, UploadResult> uploadAll(
      YearMonth month,
      Map<FundReportMapping, byte[]> pdfs,
      Map<FundReportMapping, String> linkedBeforeUpload,
      PublishProblems problems) {
    var uploads = new LinkedHashMap<FundReportMapping, UploadResult>();
    pdfs.forEach(
        (mapping, pdf) -> {
          var fundCode = mapping.fund().getCode();
          var linkedReport = Optional.ofNullable(linkedBeforeUpload.get(mapping));
          var uploadingAt = AS_THE_MEDIA_LIBRARY_SHOWS_IT.format(clock.instant());
          try {
            uploads.put(mapping, wordPressClient.upload(mapping.buildPdfFilename(month), pdf));
          } catch (Stored stored) {
            log.error("Report upload stored but unusable: fund={}", fundCode, stored);
            problems.addLeavingUploadUnlinked(
                STORED_BUT_UNUSABLE.formatted(
                    fundCode, stored.attachmentId(), stored.failedBecause()),
                "attachment " + stored.attachmentId());
          } catch (MayBeStored mayBeStored) {
            log.error("Report upload may have reached WordPress: fund={}", fundCode, mayBeStored);
            problems.add(
                linkedReport
                    .map(
                        linked ->
                            MAY_BE_STORED_BESIDE_THE_LINKED_REPORT.formatted(
                                fundCode, uploadingAt, linked, mayBeStored.failedBecause()))
                    .orElseGet(
                        () ->
                            MAY_BE_STORED.formatted(
                                fundCode, uploadingAt, mayBeStored.failedBecause())));
          } catch (NothingStored nothingStored) {
            log.error("Report upload stored nothing: fund={}", fundCode, nothingStored);
            problems.add(nothingStored(fundCode, linkedReport, nothingStored.failedBecause()));
          } catch (RuntimeException beforeAnyRequest) {
            log.error("Report upload did not start: fund={}", fundCode, beforeAnyRequest);
            problems.add(
                nothingStored(fundCode, linkedReport, beforeAnyRequest.getClass().getSimpleName()));
          }
        });
    return uploads;
  }

  private static String nothingStored(
      String fundCode, Optional<String> linkedReport, String cause) {
    return linkedReport
        .map(linked -> NOTHING_STORED_BESIDE_THE_LINKED_REPORT.formatted(fundCode, linked, cause))
        .orElseGet(() -> NOTHING_STORED.formatted(fundCode, cause));
  }
}
