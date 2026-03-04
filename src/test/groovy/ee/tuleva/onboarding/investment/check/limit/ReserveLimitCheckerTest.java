package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReserveLimitCheckerTest {

  private final ReserveLimitChecker checker = new ReserveLimitChecker();

  @Test
  void cashAboveSoftLimit() {
    var fundLimit = fundLimit(50000, 30000);
    var cashBalance = new BigDecimal("60000");

    var breach = checker.check(TUK75, cashBalance, fundLimit);

    assertThat(breach).isNotNull();
    assertThat(breach.severity()).isEqualTo(OK);
  }

  @Test
  void cashBelowSoftAboveHard() {
    var fundLimit = fundLimit(50000, 30000);
    var cashBalance = new BigDecimal("40000");

    var breach = checker.check(TUK75, cashBalance, fundLimit);

    assertThat(breach).isNotNull();
    assertThat(breach.severity()).isEqualTo(SOFT);
    assertThat(breach.cashBalance()).isEqualByComparingTo(new BigDecimal("40000"));
    assertThat(breach.reserveSoft()).isEqualByComparingTo(new BigDecimal("50000"));
    assertThat(breach.reserveHard()).isEqualByComparingTo(new BigDecimal("30000"));
    assertThat(breach.fund()).isEqualTo(TUK75);
  }

  @Test
  void cashBelowHardLimit() {
    var fundLimit = fundLimit(50000, 30000);
    var cashBalance = new BigDecimal("20000");

    var breach = checker.check(TUK75, cashBalance, fundLimit);

    assertThat(breach).isNotNull();
    assertThat(breach.severity()).isEqualTo(HARD);
  }

  @Test
  void noFundLimitConfiguredReturnsNull() {
    var breach = checker.check(TUK75, new BigDecimal("60000"), null);

    assertThat(breach).isNull();
  }

  private FundLimit fundLimit(double reserveSoft, double reserveHard) {
    return FundLimit.builder()
        .fund(TUK75)
        .reserveSoft(BigDecimal.valueOf(reserveSoft))
        .reserveHard(BigDecimal.valueOf(reserveHard))
        .build();
  }
}
