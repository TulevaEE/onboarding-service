package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.ISIN_MATCH;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IsinMatchCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private final IsinMatchChecker checker = new IsinMatchChecker();

  @Test
  void noFindingsWhenAllIsinsInModelPortfolio() {
    var positions = List.of(securityPosition("IE00BFG1TM61", new BigDecimal("1000")));
    var allocations = List.of(allocation("IE00BFG1TM61"));

    var findings = checker.check(TUK75, positions, allocations);

    assertThat(findings).isEmpty();
  }

  @Test
  void failsWhenIsinNotInModelPortfolio() {
    var positions = List.of(securityPosition("IE00UNKNOWN", new BigDecimal("1000")));
    var allocations = List.of(allocation("IE00BFG1TM61"));

    var findings = checker.check(TUK75, positions, allocations);

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.fund()).isEqualTo(TUK75);
              assertThat(f.checkType()).isEqualTo(ISIN_MATCH);
              assertThat(f.severity()).isEqualTo(FAIL);
            });
  }

  @Test
  void failsWhenQuantityIsNull() {
    var positions = List.of(securityPosition("IE00BFG1TM61", null));
    var allocations = List.of(allocation("IE00BFG1TM61"));

    var findings = checker.check(TUK75, positions, allocations);

    assertThat(findings).singleElement().satisfies(f -> assertThat(f.severity()).isEqualTo(FAIL));
  }

  @Test
  void failsWhenQuantityIsZero() {
    var positions = List.of(securityPosition("IE00BFG1TM61", BigDecimal.ZERO));
    var allocations = List.of(allocation("IE00BFG1TM61"));

    var findings = checker.check(TUK75, positions, allocations);

    assertThat(findings).singleElement().satisfies(f -> assertThat(f.severity()).isEqualTo(FAIL));
  }

  @Test
  void reportsMultipleIssues() {
    var positions =
        List.of(
            securityPosition("IE00UNKNOWN", null),
            securityPosition("IE00BFG1TM61", new BigDecimal("500")));
    var allocations = List.of(allocation("IE00BFG1TM61"));

    var findings = checker.check(TUK75, positions, allocations);

    assertThat(findings).hasSize(2);
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

  private ModelPortfolioAllocation allocation(String isin) {
    return ModelPortfolioAllocation.builder().fund(TUK75).isin(isin).build();
  }
}
