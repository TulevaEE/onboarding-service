package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressPageReader;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NewerReportCheck {

  private final WordPressPageReader pageReader;

  List<String> reasonsNotToReplace(
      List<FundReportMapping> reports, YearMonth month, UnrecognisedReportFile unrecognised) {
    return reports.stream()
        .flatMap(mapping -> reasonNotToReplace(mapping, month, unrecognised).stream())
        .toList();
  }

  private Optional<String> reasonNotToReplace(
      FundReportMapping mapping, YearMonth month, UnrecognisedReportFile unrecognised) {
    var fundCode = mapping.fund().getCode();
    try {
      return pageReader
          .investmentReportUrl(mapping.pageSlug())
          .flatMap(linked -> reasonNotToReplace(mapping, linked, month, unrecognised));
    } catch (RuntimeException e) {
      log.error("Could not read fund page to check for a newer report: fund={}", fundCode, e);
      return Optional.of(
          "Could not read the fund page to check it links no newer report: fund=%s, error=%s"
              .formatted(fundCode, e.getMessage()));
    }
  }

  private static Optional<String> reasonNotToReplace(
      FundReportMapping mapping,
      String linked,
      YearMonth month,
      UnrecognisedReportFile unrecognised) {
    var fundCode = mapping.fund().getCode();
    var linkedPeriod = mapping.reportPeriodLinkedBy(linked);
    if (linkedPeriod.isEmpty()) {
      return unrecognised == UnrecognisedReportFile.REPLACE
          ? Optional.empty()
          : Optional.of(
              ("Fund page links a file that is not this fund's report, so it may be newer; check"
                      + " it, then publish with replaceUnrecognised=true to replace it: fund=%s,"
                      + " linked=%s, publishing=%s")
                  .formatted(fundCode, linked, month));
    }
    return linkedPeriod
        .filter(period -> period.isAfter(month))
        .map(
            period ->
                "Fund page already links a newer report: fund=%s, linked=%s, publishing=%s"
                    .formatted(fundCode, period, month));
  }
}
