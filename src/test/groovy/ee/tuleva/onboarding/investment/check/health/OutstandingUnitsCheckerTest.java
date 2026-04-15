package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.OUTSTANDING_UNITS;
import static ee.tuleva.onboarding.investment.position.AccountType.UNITS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutstandingUnitsCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private final OutstandingUnitsChecker checker = new OutstandingUnitsChecker();

  @Test
  void noFindingsWhenUnitsPositionWithValidQuantity() {
    var positions = List.of(unitsPosition(new BigDecimal("1000000")));

    var findings = checker.check(TUK75, NAV_DATE, positions);

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenNoUnitsPosition() {
    var findings = checker.check(TUK75, NAV_DATE, List.of());

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.fund()).isEqualTo(TUK75);
              assertThat(f.checkType()).isEqualTo(OUTSTANDING_UNITS);
              assertThat(f.severity()).isEqualTo(WARNING);
            });
  }

  @Test
  void warnsWhenQuantityIsNull() {
    var positions = List.of(unitsPosition(null));

    var findings = checker.check(TUK75, NAV_DATE, positions);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void warnsWhenQuantityIsZero() {
    var positions = List.of(unitsPosition(BigDecimal.ZERO));

    var findings = checker.check(TUK75, NAV_DATE, positions);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  private FundPosition unitsPosition(BigDecimal quantity) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(UNITS)
        .accountName("Total outstanding units")
        .quantity(quantity)
        .build();
  }
}
