package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.FUND_IN_REPORT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FundInReportCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private final FundInReportChecker checker = new FundInReportChecker();

  @Test
  void warnsAboutANavFundTheReportHasNoRowsFor() {
    var findings = checker.fundsMissingFromReport(NAV_DATE, Set.of(TUK75, TUK00, TKF100));

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUV100,
                FUND_IN_REPORT,
                WARNING,
                "TUV100: not in SEB's position report for navDate=2026-04-15. Ask SEB to resend"
                    + " the report with TUV100 before the 15:20 NAV"));
  }

  @Test
  void warnsAboutEveryStartedNavFundWhenTheReportHasNoFundRowsAtAll() {
    assertThat(checker.fundsMissingFromReport(NAV_DATE, Set.of()))
        .extracting(HealthCheckFinding::fund)
        .containsExactly(TUK75, TUK00, TUV100, TKF100);
  }

  @Test
  void noFundIsMissingFromAReportWithEveryNavFund() {
    assertThat(checker.fundsMissingFromReport(NAV_DATE, EnumSet.allOf(TulevaFund.class))).isEmpty();
  }

  @Test
  void aFundIsNotMissingFromAReportDatedBeforeItsInception() {
    var dayBeforeInception = TKF100.getInceptionDate().minusDays(1);

    assertThat(checker.fundsMissingFromReport(dayBeforeInception, Set.of(TUK75, TUK00, TUV100)))
        .isEmpty();
  }
}
