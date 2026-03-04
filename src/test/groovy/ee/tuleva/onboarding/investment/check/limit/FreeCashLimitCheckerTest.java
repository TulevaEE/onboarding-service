package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FreeCashLimitCheckerTest {

  private final FreeCashLimitChecker checker = new FreeCashLimitChecker();

  @Test
  void freeCashWithinLimit() {
    var fundLimit = fundLimit(50000, 30000, 20000);
    var cashTotal = new BigDecimal("80000");
    var liabilityTotal = new BigDecimal("-15000");

    var breach = checker.check(TUK75, cashTotal, liabilityTotal, fundLimit);

    // freeCash = 80000 + (-15000) - 50000 = 15000
    assertThat(breach).isNotNull();
    assertThat(breach.severity()).isEqualTo(OK);
    assertThat(breach.freeCash()).isEqualByComparingTo(new BigDecimal("15000"));
    assertThat(breach.maxFreeCash()).isEqualByComparingTo(new BigDecimal("20000"));
  }

  @Test
  void freeCashExceedsLimit() {
    var fundLimit = fundLimit(50000, 30000, 10000);
    var cashTotal = new BigDecimal("80000");
    var liabilityTotal = new BigDecimal("-15000");

    var breach = checker.check(TUK75, cashTotal, liabilityTotal, fundLimit);

    // freeCash = 80000 + (-15000) - 50000 = 15000 > 10000 → HARD
    assertThat(breach).isNotNull();
    assertThat(breach.severity()).isEqualTo(HARD);
    assertThat(breach.freeCash()).isEqualByComparingTo(new BigDecimal("15000"));
    assertThat(breach.maxFreeCash()).isEqualByComparingTo(new BigDecimal("10000"));
  }

  @Test
  void noMaxFreeCashConfiguredReturnsNull() {
    var fundLimit =
        FundLimit.builder()
            .fund(TUK75)
            .reserveSoft(BigDecimal.valueOf(50000))
            .reserveHard(BigDecimal.valueOf(30000))
            .build();

    var breach = checker.check(TUK75, new BigDecimal("80000"), new BigDecimal("15000"), fundLimit);

    assertThat(breach).isNull();
  }

  @Test
  void noFundLimitReturnsNull() {
    var breach = checker.check(TUK75, new BigDecimal("80000"), new BigDecimal("15000"), null);

    assertThat(breach).isNull();
  }

  private FundLimit fundLimit(double reserveSoft, double reserveHard, double maxFreeCash) {
    return FundLimit.builder()
        .fund(TUK75)
        .reserveSoft(BigDecimal.valueOf(reserveSoft))
        .reserveHard(BigDecimal.valueOf(reserveHard))
        .maxFreeCash(BigDecimal.valueOf(maxFreeCash))
        .build();
  }
}
