package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LimitCheckResultTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 3, 4);

  @Test
  void noBreachesWhenAllEmpty() {
    var result = new LimitCheckResult(TUK75, CHECK_DATE, List.of(), List.of(), null, null);

    assertThat(result.hasBreaches()).isFalse();
  }

  @Test
  void positionBreachDetected() {
    var breach =
        new PositionBreach(
            TUK75,
            "IE001",
            "iShares",
            new BigDecimal("16"),
            new BigDecimal("15"),
            new BigDecimal("20"),
            SOFT);
    var result = new LimitCheckResult(TUK75, CHECK_DATE, List.of(breach), List.of(), null, null);

    assertThat(result.hasBreaches()).isTrue();
  }

  @Test
  void reserveBreachDetected() {
    var breach =
        new ReserveBreach(
            TUK75, new BigDecimal("40000"), new BigDecimal("50000"), new BigDecimal("30000"), SOFT);
    var result = new LimitCheckResult(TUK75, CHECK_DATE, List.of(), List.of(), breach, null);

    assertThat(result.hasBreaches()).isTrue();
  }

  @Test
  void freeCashBreachAloneIsNotABreach() {
    var breach = new FreeCashBreach(TUK75, new BigDecimal("25000"), new BigDecimal("10000"), HARD);
    var result = new LimitCheckResult(TUK75, CHECK_DATE, List.of(), List.of(), null, breach);

    assertThat(result.hasBreaches()).isFalse();
  }

  @Test
  void okSeverityIsNotABreach() {
    var positionBreach =
        new PositionBreach(
            TUK75,
            "IE001",
            "iShares",
            new BigDecimal("14"),
            new BigDecimal("15"),
            new BigDecimal("20"),
            OK);
    var reserveBreach =
        new ReserveBreach(
            TUK75, new BigDecimal("60000"), new BigDecimal("50000"), new BigDecimal("30000"), OK);
    var result =
        new LimitCheckResult(
            TUK75, CHECK_DATE, List.of(positionBreach), List.of(), reserveBreach, null);

    assertThat(result.hasBreaches()).isFalse();
  }
}
