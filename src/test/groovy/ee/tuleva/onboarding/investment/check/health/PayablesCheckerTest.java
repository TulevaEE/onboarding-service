package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayablesCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private final PayablesChecker checker = new PayablesChecker();

  @Test
  void noFindingsWhenQuantityIncreasedAndLiabilitiesExist() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1100")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var liabilities = List.of(liabilityPosition(new BigDecimal("-5000")));

    var findings = checker.check(TUK75, today, previous, liabilities);

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenQuantityIncreasedButNoLiabilities() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1100")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, List.of());

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.fund()).isEqualTo(TUK75);
              assertThat(f.checkType()).isEqualTo(HealthCheckType.PAYABLES);
              assertThat(f.severity()).isEqualTo(WARNING);
            });
  }

  @Test
  void noFindingsWhenNoQuantityIncrease() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, List.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingsWhenNoPreviousDayData() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, List.of(), List.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenQuantityIncreasedAndLiabilitiesHaveZeroMarketValue() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1100")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var liabilities = List.of(liabilityPosition(BigDecimal.ZERO));

    var findings = checker.check(TUK75, today, previous, liabilities);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  private FundPosition securityPosition(String isin, BigDecimal quantity) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(SECURITY)
        .accountName("iShares")
        .accountId(isin)
        .quantity(quantity)
        .build();
  }

  private FundPosition liabilityPosition(BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(AccountType.LIABILITY)
        .accountName("Total payables of unsettled transactions")
        .marketValue(marketValue)
        .build();
  }
}
