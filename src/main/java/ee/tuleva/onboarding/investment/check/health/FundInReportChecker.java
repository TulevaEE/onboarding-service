package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.FUND_IN_REPORT;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalendar;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FundInReportChecker {

  private static final ZoneId NAV_ZONE = ZoneId.of("Europe/Tallinn");

  private final FundNavQueryService fundNavQueryService;
  private final NavCalendar navCalendar;
  private final Clock clock;

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

  private String notInReport(TulevaFund fund, LocalDate navDate) {
    return beforeTheNavStillToCome(
        "%s: not in SEB's position report for navDate=%s. Ask SEB to resend the report with %s"
            .formatted(fund, navDate, fund),
        navDate,
        List.of(fund));
  }

  private String noDataRows(LocalDate navDate, List<TulevaFund> funds) {
    return beforeTheNavStillToCome(
        "SEB's position report for navDate=%s has no data rows. Ask SEB to resend it"
            .formatted(navDate),
        navDate,
        funds);
  }

  private String beforeTheNavStillToCome(String askSeb, LocalDate navDate, List<TulevaFund> funds) {
    return earliestNavStillToCome(navDate, funds)
        .map(cutoff -> "%s before the %s NAV".formatted(askSeb, cutoff))
        .orElse(askSeb);
  }

  private Optional<LocalTime> earliestNavStillToCome(LocalDate navDate, List<TulevaFund> funds) {
    return funds.stream()
        .filter(fund -> navStillToCome(fund, navDate))
        .map(TulevaFund::getNavCutoffTime)
        .min(naturalOrder());
  }

  private boolean navStillToCome(TulevaFund fund, LocalDate navDate) {
    var today = LocalDate.ofInstant(clock.instant(), NAV_ZONE);
    return !navCalendar.calculationDateOf(navDate).isBefore(today)
        && !fundNavQueryService.hasPublishedNav(fund.getCode(), navDate);
  }

  private static String rowsNotRead(LocalDate navDate, int reportRows) {
    return ("none of the %d rows in SEB's position report for navDate=%s could be read as fund"
            + " positions: check the file and the parser")
        .formatted(reportRows, navDate);
  }
}
