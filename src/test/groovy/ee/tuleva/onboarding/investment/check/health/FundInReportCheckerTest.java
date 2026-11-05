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
  private static final int POSITION_ROWS = 40;
  private static final Set<TulevaFund> ALL_BUT_TUV100 = Set.of(TUK75, TUK00, TKF100);

  @Mock FundNavQueryService fundNavQueryService;

  @Test
  void warnsAboutANavFundTheReportHasNoRowsFor() {
    var results =
        checkerAt(MORNING_OF_THE_NAV_DAY)
            .fundsNotRead(NAV_DATE, ALL_BUT_TUV100, POSITION_ROWS, ALL_BUT_TUV100);

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
            .fundsNotRead(NAV_DATE, ALL_BUT_TUV100, POSITION_ROWS, ALL_BUT_TUV100);

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
            .fundsNotRead(NAV_DATE, ALL_BUT_TUV100, POSITION_ROWS, ALL_BUT_TUV100);

    assertThat(results)
        .containsExactly(
            missingFromReport(
                TUV100,
                "TUV100: not in SEB's position report for navDate=2026-04-15. Ask SEB to resend"
                    + " the report with TUV100"));
  }

  @Test
  void aReportWithNoPositionRowsAsksSebToResendItBeforeEachFundsOwnNav() {
    var beforeTheMorningNav = NO_FUND_ROWS + " before the 11:00 NAV";
    var beforeTheAfternoonNav = NO_FUND_ROWS + " before the 15:20 NAV";

    assertThat(checkerAt(MORNING_OF_THE_NAV_DAY).fundsNotRead(NAV_DATE, Set.of(), 0, Set.of()))
        .containsExactly(
            missingFromReport(TUK75, beforeTheMorningNav),
            missingFromReport(TUK00, beforeTheMorningNav),
            missingFromReport(TUV100, beforeTheAfternoonNav),
            missingFromReport(TKF100, beforeTheAfternoonNav));
  }

  @Test
  void aReportWithNoPositionRowsGivesNoDeadlineToAFundWhoseNavForThatDateIsPublished() {
    given(fundNavQueryService.hasPublishedNav("TUK75", NAV_DATE)).willReturn(true);
    given(fundNavQueryService.hasPublishedNav("TUK00", NAV_DATE)).willReturn(true);
    given(fundNavQueryService.hasPublishedNav("TUV100", NAV_DATE)).willReturn(false);
    given(fundNavQueryService.hasPublishedNav("TKF100", NAV_DATE)).willReturn(false);
    var beforeTheAfternoonNav = NO_FUND_ROWS + " before the 15:20 NAV";

    assertThat(checkerAt("2026-04-16T09:00:00Z").fundsNotRead(NAV_DATE, Set.of(), 0, Set.of()))
        .containsExactly(
            missingFromReport(TUK75, NO_FUND_ROWS),
            missingFromReport(TUK00, NO_FUND_ROWS),
            missingFromReport(TUV100, beforeTheAfternoonNav),
            missingFromReport(TKF100, beforeTheAfternoonNav));
  }

  @Test
  void saysTheNavIsNotPublishedYetOncePastItsCutoffOnTheNavDay() {
    var results =
        checkerAt("2026-04-16T13:00:00Z")
            .fundsNotRead(NAV_DATE, ALL_BUT_TUV100, POSITION_ROWS, ALL_BUT_TUV100);

    assertThat(results)
        .containsExactly(
            missingFromReport(
                TUV100,
                "TUV100: not in SEB's position report for navDate=2026-04-15. Ask SEB to resend"
                    + " the report with TUV100; the 15:20 NAV for this date is not published yet"));
  }

  @Test
  void onAFundsInceptionDayItsNavForThatDateIsTheSameDaysNav() {
    var inception = TKF100.getInceptionDate();
    var fundsBeforeTkf100 = Set.of(TUK75, TUK00, TUV100);

    var results =
        checkerAt("2026-02-02T14:00:00Z")
            .fundsNotRead(inception, fundsBeforeTkf100, POSITION_ROWS, fundsBeforeTkf100);

    assertThat(results)
        .containsExactly(
            HealthCheckResult.missingFromReport(
                inception,
                new HealthCheckFinding(
                    TKF100,
                    FUND_IN_REPORT,
                    WARNING,
                    "TKF100: not in SEB's position report for navDate=2026-02-02. Ask SEB to"
                        + " resend the report with TKF100; the 15:20 NAV for this date is not"
                        + " published yet")));
  }

  @Test
  void aReportWhoseRowsCouldNotBeReadPointsAtTheFileAndTheParserInsteadOfAskingSebToResend() {
    assertThat(
            checkerAt(MORNING_OF_THE_NAV_DAY)
                .fundsNotRead(NAV_DATE, EnumSet.allOf(TulevaFund.class), POSITION_ROWS, Set.of()))
        .containsExactly(
            notReadFromReport(TUK75, ROWS_NOT_READ + " before the 11:00 NAV"),
            notReadFromReport(TUK00, ROWS_NOT_READ + " before the 11:00 NAV"),
            notReadFromReport(TUV100, ROWS_NOT_READ + " before the 15:20 NAV"),
            notReadFromReport(TKF100, ROWS_NOT_READ + " before the 15:20 NAV"));
  }

  @Test
  void aReportWhosePositionRowsNameNoFundIsPutOnTheFileAndTheParserForEveryFund() {
    var noRowNamesAFund =
        "none of the 40 position rows in SEB's position report for navDate=2026-04-15 names a"
            + " fund: check the file and the parser";

    assertThat(
            checkerAt(MORNING_OF_THE_NAV_DAY)
                .fundsNotRead(NAV_DATE, Set.of(), POSITION_ROWS, Set.of()))
        .containsExactly(
            notReadFromReport(TUK75, noRowNamesAFund + " before the 11:00 NAV"),
            notReadFromReport(TUK00, noRowNamesAFund + " before the 11:00 NAV"),
            notReadFromReport(TUV100, noRowNamesAFund + " before the 15:20 NAV"),
            notReadFromReport(TKF100, noRowNamesAFund + " before the 15:20 NAV"));
  }

  @Test
  void aFundWhoseRowsCouldNotBeReadWhileOtherFundsWereIsPutOnTheParserAndAMissingOneOnSeb() {
    assertThat(
            checkerAt(MORNING_OF_THE_NAV_DAY)
                .fundsNotRead(NAV_DATE, ALL_BUT_TUV100, POSITION_ROWS, Set.of(TUK75, TUK00)))
        .containsExactly(
            missingFromReport(
                TUV100,
                "TUV100: not in SEB's position report for navDate=2026-04-15. Ask SEB to resend"
                    + " the report with TUV100 before the 15:20 NAV"),
            notReadFromReport(TKF100, ROWS_NOT_READ + " before the 15:20 NAV"));
  }

  @Test
  void noFundIsMissingFromAReportWithEveryNavFund() {
    var everyFund = EnumSet.allOf(TulevaFund.class);

    assertThat(
            checkerAt(MORNING_OF_THE_NAV_DAY)
                .fundsNotRead(NAV_DATE, everyFund, POSITION_ROWS, everyFund))
        .isEmpty();
  }

  @Test
  void aFundIsNotMissingFromAReportDatedBeforeItsInception() {
    var dayBeforeInception = TKF100.getInceptionDate().minusDays(1);
    var fundsBeforeTkf100 = Set.of(TUK75, TUK00, TUV100);

    assertThat(
            checkerAt(MORNING_OF_THE_NAV_DAY)
                .fundsNotRead(
                    dayBeforeInception, fundsBeforeTkf100, POSITION_ROWS, fundsBeforeTkf100))
        .isEmpty();
  }

  private static final String ROWS_NOT_READ =
      "rows in SEB's position report for navDate=2026-04-15 name the fund but none could be read"
          + " as fund positions: check the file and the parser";

  private static final String NO_FUND_ROWS =
      "SEB's position report for navDate=2026-04-15 has no position rows. Ask SEB to resend it";

  private FundInReportChecker checkerAt(String instant) {
    return new FundInReportChecker(
        fundNavQueryService, new PublicHolidays(), Clock.fixed(Instant.parse(instant), UTC));
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
