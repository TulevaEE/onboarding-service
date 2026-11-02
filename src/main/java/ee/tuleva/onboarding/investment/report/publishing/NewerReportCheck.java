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

  List<String> newerReportsLinked(List<FundReportMapping> reports, YearMonth month) {
    return reports.stream()
        .flatMap(mapping -> newerReportLinkedFrom(mapping, month).stream())
        .toList();
  }

  private Optional<String> newerReportLinkedFrom(FundReportMapping mapping, YearMonth month) {
    var fundCode = mapping.fund().getCode();
    try {
      return pageReader
          .investmentReportUrl(mapping.pageSlug())
          .flatMap(mapping::reportPeriodLinkedBy)
          .filter(linked -> linked.isAfter(month))
          .map(
              linked ->
                  "Fund page already links a newer report: fund=%s, linked=%s, publishing=%s"
                      .formatted(fundCode, linked, month));
    } catch (RuntimeException e) {
      log.error("Could not read fund page to check for a newer report: fund={}", fundCode, e);
      return Optional.of(
          "Could not read the fund page to check it links no newer report: fund=%s, error=%s"
              .formatted(fundCode, e.getMessage()));
    }
  }
}
