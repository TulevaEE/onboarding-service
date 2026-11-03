package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.FUND_IN_REPORT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalendar;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundInReportCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private static final String MORNING_OF_THE_NAV_DAY = "2026-04-16T06:00:00Z";
  private static final int REPORT_ROWS = 12;

  @Mock FundNavQueryService fundNavQueryService;

  @Test
  void warnsAboutANavFundTheReportHasNoRowsFor() {
    var results =
        checkerAt(MORNING_OF_THE_NAV_DAY)
            .fundsNotRead(NAV_DATE, Set.of(TUK75, TUK00, TKF100), REPORT_ROWS);

    assertThat(results)
        .containsExactly(
            missingFromReport(
                TUV100,
                "TUV100: not in SEB's position report for navDate=2026-04-15. Ask SEB to resend"
                    + " the report with TUV100 before the 15:20 NAV"));
  }

  @Test
  void leavesTheNavDeadlineOutOnceTheNavForThatDateIsPublished() {
    given(fundNavQueryService.hasPublishedNav("TUV100", NAV_DATE)).willReturn(true);

    var results =
        checkerAt("2026-04-16T14:00:00Z")
            .fundsNotRead(NAV_DATE, Set.of(TUK75, TUK00, TKF100), REPORT_ROWS);

    assertThat(results)
        .containsExactly(
            missingFromReport(
                TUV100,
                "TUV100: not in SEB's position report for navDate=2026-04-15. Ask SEB to resend"
                    + " the report with TUV100"));
  }

  @Test
  void leavesTheNavDeadlineOutForADateWhoseNavDayHasPassed() {
    var results =
        checkerAt("2026-04-20T06:00:00Z")
            .fundsNotRead(NAV_DATE, Set.of(TUK75, TUK00, TKF100), REPORT_ROWS);

    assertThat(results)
        .containsExactly(
            missingFromReport(
                TUV100,
                "TUV100: not in SEB's position report for navDate=2026-04-15. Ask SEB to resend"
                    + " the report with TUV100"));
  }

  @Test
  void aReportWithNoDataRowsAsksSebToResendItWithOneMessageForEveryStartedNavFund() {
    var noDataRows =
        "SEB's position report for navDate=2026-04-15 has no data rows. Ask SEB to resend it"
            + " before the 11:00 NAV";

    assertThat(checkerAt(MORNING_OF_THE_NAV_DAY).fundsNotRead(NAV_DATE, Set.of(), 0))
        .containsExactly(
            missingFromReport(TUK75, noDataRows),
            missingFromReport(TUK00, noDataRows),
            missingFromReport(TUV100, noDataRows),
            missingFromReport(TKF100, noDataRows));
  }

  @Test
  void aReportWithNoDataRowsGivesTheEarliestNavOfItsDateThatIsStillToCome() {
    given(fundNavQueryService.hasPublishedNav("TUK75", NAV_DATE)).willReturn(true);
    given(fundNavQueryService.hasPublishedNav("TUK00", NAV_DATE)).willReturn(true);
    given(fundNavQueryService.hasPublishedNav("TUV100", NAV_DATE)).willReturn(false);
    given(fundNavQueryService.hasPublishedNav("TKF100", NAV_DATE)).willReturn(false);
    var noDataRows =
        "SEB's position report for navDate=2026-04-15 has no data rows. Ask SEB to resend it"
            + " before the 15:20 NAV";

    assertThat(checkerAt("2026-04-16T09:00:00Z").fundsNotRead(NAV_DATE, Set.of(), 0))
        .containsExactly(
            missingFromReport(TUK75, noDataRows),
            missingFromReport(TUK00, noDataRows),
            missingFromReport(TUV100, noDataRows),
            missingFromReport(TKF100, noDataRows));
  }

  @Test
  void aReportWhoseRowsCouldNotBeReadPointsAtTheFileAndTheParserInsteadOfAskingSebToResend() {
    var rowsNotRead =
        "none of the 12 rows in SEB's position report for navDate=2026-04-15 could be read as"
            + " fund positions: check the file and the parser";

    assertThat(checkerAt(MORNING_OF_THE_NAV_DAY).fundsNotRead(NAV_DATE, Set.of(), REPORT_ROWS))
        .containsExactly(
            notReadFromReport(TUK75, rowsNotRead),
            notReadFromReport(TUK00, rowsNotRead),
            notReadFromReport(TUV100, rowsNotRead),
            notReadFromReport(TKF100, rowsNotRead));
  }

  @Test
  void noFundIsMissingFromAReportWithEveryNavFund() {
    assertThat(
            checkerAt(MORNING_OF_THE_NAV_DAY)
                .fundsNotRead(NAV_DATE, EnumSet.allOf(TulevaFund.class), REPORT_ROWS))
        .isEmpty();
  }

  @Test
  void aFundIsNotMissingFromAReportDatedBeforeItsInception() {
    var dayBeforeInception = TKF100.getInceptionDate().minusDays(1);

    assertThat(
            checkerAt(MORNING_OF_THE_NAV_DAY)
                .fundsNotRead(dayBeforeInception, Set.of(TUK75, TUK00, TUV100), REPORT_ROWS))
        .isEmpty();
  }

  private FundInReportChecker checkerAt(String instant) {
    return new FundInReportChecker(
        fundNavQueryService,
        new NavCalendar(new PublicHolidays()),
        Clock.fixed(Instant.parse(instant), UTC));
  }

  private static HealthCheckResult missingFromReport(TulevaFund fund, String message) {
    return HealthCheckResult.missingFromReport(
        NAV_DATE, new HealthCheckFinding(fund, FUND_IN_REPORT, WARNING, message));
  }

  private static HealthCheckResult notReadFromReport(TulevaFund fund, String message) {
    return HealthCheckResult.notReadFromReport(
        NAV_DATE, new HealthCheckFinding(fund, FUND_IN_REPORT, WARNING, message));
  }
}
