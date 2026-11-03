package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.FUND_IN_REPORT;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class FundInReportChecker {

  List<HealthCheckResult> fundsNotRead(
      LocalDate navDate, Set<TulevaFund> fundsRead, int reportRows) {
    var fundsNotRead =
        Arrays.stream(TulevaFund.values())
            .filter(TulevaFund::hasNavCalculation)
            .filter(fund -> !navDate.isBefore(fund.getInceptionDate()))
            .filter(fund -> !fundsRead.contains(fund))
            .toList();
    if (fundsNotRead.isEmpty()) {
      return List.of();
    }
    if (!fundsRead.isEmpty()) {
      return fundsNotRead.stream()
          .map(fund -> missingFromReport(navDate, fund, notInReport(fund, navDate)))
          .toList();
    }
    if (reportRows == 0) {
      var noDataRows = noDataRows(navDate, fundsNotRead);
      return fundsNotRead.stream()
          .map(fund -> missingFromReport(navDate, fund, noDataRows))
          .toList();
    }
    var rowsNotRead = rowsNotRead(navDate, reportRows);
    return fundsNotRead.stream()
        .map(
            fund ->
                HealthCheckResult.notReadFromReport(navDate, presenceWarning(fund, rowsNotRead)))
        .toList();
  }

  private static HealthCheckResult missingFromReport(
      LocalDate navDate, TulevaFund fund, String message) {
    return HealthCheckResult.missingFromReport(navDate, presenceWarning(fund, message));
  }

  private static HealthCheckFinding presenceWarning(TulevaFund fund, String message) {
    return new HealthCheckFinding(fund, FUND_IN_REPORT, WARNING, message);
  }

  private static String notInReport(TulevaFund fund, LocalDate navDate) {
    return ("%s: not in SEB's position report for navDate=%s. Ask SEB to resend the report with %s"
            + " before the %s NAV")
        .formatted(fund, navDate, fund, fund.getNavCutoffTime());
  }

  private static String noDataRows(LocalDate navDate, List<TulevaFund> funds) {
    return ("SEB's position report for navDate=%s has no data rows. Ask SEB to resend it"
            + " before the %s NAV")
        .formatted(
            navDate,
            funds.stream().map(TulevaFund::getNavCutoffTime).min(naturalOrder()).orElseThrow());
  }

  private static String rowsNotRead(LocalDate navDate, int reportRows) {
    return ("none of the %d rows in SEB's position report for navDate=%s could be read as fund"
            + " positions: check the file and the parser")
        .formatted(reportRows, navDate);
  }
}
