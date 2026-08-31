package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.ISIN_MATCH;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class IsinMatchChecker {

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> securities,
      List<ModelPortfolioAllocation> allocations,
      List<ModelPortfolioAllocation> previousAllocations) {
    Set<String> currentIsins = isinsOf(allocations);
    Set<String> previousIsins = isinsOf(previousAllocations);

    var findings = new ArrayList<HealthCheckFinding>();
    for (var position : securities) {
      String isin = position.getAccountId();
      checkIsinPresence(fund, isin, currentIsins, previousIsins).ifPresent(findings::add);
      checkQuantity(fund, position, isin, currentIsins, previousIsins).ifPresent(findings::add);
    }

    return findings;
  }

  private static Set<String> isinsOf(List<ModelPortfolioAllocation> allocations) {
    return allocations.stream()
        .map(ModelPortfolioAllocation::getIsin)
        .filter(isin -> isin != null)
        .collect(Collectors.toSet());
  }

  private Optional<HealthCheckFinding> checkIsinPresence(
      TulevaFund fund, @Nullable String isin, Set<String> currentIsins, Set<String> previousIsins) {
    if (isin != null && currentIsins.contains(isin)) {
      return Optional.empty();
    }
    if (isin != null && previousIsins.contains(isin)) {
      return Optional.of(
          new HealthCheckFinding(
              fund,
              ISIN_MATCH,
              WARNING,
              "%s: ISIN %s not in current model portfolio but present in previous (in-runoff)"
                  .formatted(fund, isin)));
    }
    return Optional.of(
        new HealthCheckFinding(
            fund, ISIN_MATCH, FAIL, "%s: ISIN %s not in model portfolio".formatted(fund, isin)));
  }

  private Optional<HealthCheckFinding> checkQuantity(
      TulevaFund fund,
      FundPosition position,
      @Nullable String isin,
      Set<String> currentIsins,
      Set<String> previousIsins) {
    if (position.getQuantity() != null && position.getQuantity().compareTo(ZERO) != 0) {
      return Optional.empty();
    }
    boolean inCurrentModelPortfolio = isin != null && currentIsins.contains(isin);
    boolean inPreviousModelPortfolio = isin != null && previousIsins.contains(isin);
    if (inCurrentModelPortfolio && inPreviousModelPortfolio) {
      return Optional.of(
          new HealthCheckFinding(
              fund,
              ISIN_MATCH,
              FAIL,
              "%s: quantity is %s for ISIN %s (held in model but position emptied?)"
                  .formatted(fund, position.getQuantity(), isin)));
    }
    if (inPreviousModelPortfolio) {
      return Optional.of(
          new HealthCheckFinding(
              fund,
              ISIN_MATCH,
              WARNING,
              "%s: quantity is %s for ISIN %s (in-runoff — fully liquidated)"
                  .formatted(fund, position.getQuantity(), isin)));
    }
    return Optional.of(
        new HealthCheckFinding(
            fund,
            ISIN_MATCH,
            WARNING,
            "%s: quantity is %s for ISIN %s (newly added / not yet bought?)"
                .formatted(fund, position.getQuantity(), isin)));
  }
}
