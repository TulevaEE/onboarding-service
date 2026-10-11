package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.HARD;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.SOFT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.check.limit.EODHDFundSizeClient.FundSize;
import ee.tuleva.onboarding.investment.check.limit.UnderlyingFunds.SizeInEur;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OwnershipLimitCheckerTest {

  private static final BigDecimal FUND_SIZE = new BigDecimal("100000000");
  private static final LocalDate UPDATED = LocalDate.of(2026, 10, 3);
  private static final OwnershipLimit LIMIT =
      new OwnershipLimit(
          TKF100, LocalDate.of(2026, 9, 30), new BigDecimal("20"), new BigDecimal("25"));

  private final OwnershipLimitChecker checker = new OwnershipLimitChecker();

  @Test
  void holdingTwentyPercentOfTheUnderlyingFund_staysOk() {
    var breach = check("20000000");

    assertThat(breach)
        .isEqualTo(
            new OwnershipBreach(
                "IE00BMDBMY19",
                "Invesco EM",
                new BigDecimal("20000000"),
                FUND_SIZE,
                new BigDecimal("117000000"),
                "USD",
                UPDATED,
                new BigDecimal("20.0000"),
                new BigDecimal("20"),
                new BigDecimal("25"),
                OK));
  }

  @Test
  void holdingJustOverTwentyPercent_firesTheSoftLimit() {
    assertThat(check("20000100").severity()).isEqualTo(SOFT);
  }

  @Test
  void holdingJustUnderTwentyFivePercent_isStillSoft() {
    assertThat(check("24999900").severity()).isEqualTo(SOFT);
  }

  @Test
  void holdingExactlyTwentyFivePercent_firesTheHardLimit() {
    assertThat(check("25000000").severity()).isEqualTo(HARD);
  }

  @Test
  void holdingOverTwentyFivePercent_isHard() {
    var breach = check("31000000");

    assertThat(breach.severity()).isEqualTo(HARD);
    assertThat(breach.actualPercent()).isEqualByComparingTo("31");
  }

  private OwnershipBreach check(String holdingValue) {
    return checker.check(
        "IE00BMDBMY19",
        "Invesco EM",
        new BigDecimal(holdingValue),
        new SizeInEur.Known(
            FUND_SIZE, new FundSize.Reported(new BigDecimal("117000000"), "USD", UPDATED)),
        LIMIT);
  }
}
