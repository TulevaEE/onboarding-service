package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.COMPLETENESS;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompletenessCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private final CompletenessChecker checker = new CompletenessChecker();

  @Test
  void noFindingsWhenSecurityAndCashPresent() {
    var positions =
        List.of(
            securityPosition("IE00BFG1TM61", new BigDecimal("1000")),
            cashPosition(new BigDecimal("50000")));

    var findings = checker.check(TUK75, NAV_DATE, positions);

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenCashMissing() {
    var positions = List.of(securityPosition("IE00BFG1TM61", new BigDecimal("1000")));

    var findings = checker.check(TUK75, NAV_DATE, positions);

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.fund()).isEqualTo(TUK75);
              assertThat(f.checkType()).isEqualTo(COMPLETENESS);
              assertThat(f.severity()).isEqualTo(WARNING);
            });
  }

  @Test
  void warnsWhenSecurityMissing() {
    var positions = List.of(cashPosition(new BigDecimal("50000")));

    var findings = checker.check(TUK75, NAV_DATE, positions);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void warnsForBothWhenEmpty() {
    var findings = checker.check(TUK75, NAV_DATE, List.of());

    assertThat(findings).hasSize(2);
    assertThat(findings).allMatch(f -> f.severity() == WARNING);
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

  private FundPosition cashPosition(BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(CASH)
        .accountName("Cash account in SEB Pank")
        .marketValue(marketValue)
        .build();
  }
}
