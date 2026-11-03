package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.FUND_IN_REPORT;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FundInReportChecker {

  private static final ZoneId NAV_ZONE = ZoneId.of("Europe/Tallinn");

  private final FundNavQueryService fundNavQueryService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  List<HealthCheckResult> fundsNotRead(
      LocalDate navDate, Set<TulevaFund> fundsWithRows, Set<TulevaFund> fundsRead) {
    var fundsNotRead =
        Arrays.stream(TulevaFund.values())
            .filter(TulevaFund::hasNavCalculation)
            .filter(fund -> !navDate.isBefore(fund.getInceptionDate()))
            .filter(fund -> !fundsRead.contains(fund))
            .toList();
    if (fundsNotRead.isEmpty()) {
      return List.of();
    }
    if (fundsWithRows.isEmpty()) {
      return fundsNotRead.stream()
          .map(fund -> missingFromReport(navDate, fund, noFundRows(fund, navDate)))
          .toList();
    }
    return fundsNotRead.stream()
        .map(
            fund ->
                fundsWithRows.contains(fund)
                    ? HealthCheckResult.notReadFromReport(
                        navDate, presenceWarning(fund, rowsNotRead(navDate)))
                    : missingFromReport(navDate, fund, notInReport(fund, navDate)))
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
    return withNavDeadline(
        "%s: not in SEB's position report for navDate=%s. Ask SEB to resend the report with %s"
            .formatted(fund, navDate, fund),
        fund,
        navDate);
  }

  private String noFundRows(TulevaFund fund, LocalDate navDate) {
    return withNavDeadline(
        "SEB's position report for navDate=%s has no fund rows. Ask SEB to resend it"
            .formatted(navDate),
        fund,
        navDate);
  }

  private String withNavDeadline(String askSeb, TulevaFund fund, LocalDate navDate) {
    return switch (navFor(fund, navDate)) {
      case STILL_TO_COME -> "%s before the %s NAV".formatted(askSeb, fund.getNavCutoffTime());
      case PAST_ITS_CUTOFF_UNPUBLISHED ->
          "%s; the %s NAV for this date is not published yet"
              .formatted(askSeb, fund.getNavCutoffTime());
      case DONE -> askSeb;
    };
  }

  private NavOfTheDate navFor(TulevaFund fund, LocalDate navDate) {
    var now = ZonedDateTime.ofInstant(clock.instant(), NAV_ZONE);
    var today = now.toLocalDate();
    var datesNavRunUses =
        NavCalculationService.expectedPositionReportDate(fund, today, publicHolidays);
    if (navDate.isBefore(datesNavRunUses)
        || fundNavQueryService.hasPublishedNav(fund.getCode(), navDate)) {
      return NavOfTheDate.DONE;
    }
    if (navDate.equals(datesNavRunUses)
        && publicHolidays.isWorkingDay(today)
        && !now.toLocalTime().isBefore(fund.getNavCutoffTime())) {
      return NavOfTheDate.PAST_ITS_CUTOFF_UNPUBLISHED;
    }
    return NavOfTheDate.STILL_TO_COME;
  }

  private enum NavOfTheDate {
    STILL_TO_COME,
    PAST_ITS_CUTOFF_UNPUBLISHED,
    DONE
  }

  private static String rowsNotRead(LocalDate navDate) {
    return ("rows in SEB's position report for navDate=%s name the fund but none could be read as"
            + " fund positions: check the file and the parser")
        .formatted(navDate);
  }
}
