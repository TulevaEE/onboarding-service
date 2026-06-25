package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.ISIN_MATCH;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class IsinMatchChecker {

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> securities,
      List<ModelPortfolioAllocation> allocations,
      List<ModelPortfolioAllocation> previousAllocations) {
    var findings = new ArrayList<HealthCheckFinding>();
    Set<String> currentIsins =
        allocations.stream()
            .map(ModelPortfolioAllocation::getIsin)
            .filter(isin -> isin != null)
            .collect(Collectors.toSet());

    Set<String> previousIsins =
        previousAllocations.stream()
            .map(ModelPortfolioAllocation::getIsin)
            .filter(isin -> isin != null)
            .collect(Collectors.toSet());

    for (var position : securities) {
      String isin = position.getAccountId();
      if (isin == null || !currentIsins.contains(isin)) {
        if (isin != null && previousIsins.contains(isin)) {
          findings.add(
              new HealthCheckFinding(
                  fund,
                  ISIN_MATCH,
                  WARNING,
                  "%s: ISIN %s not in current model portfolio but present in previous (in-runoff)"
                      .formatted(fund, isin)));
        } else {
          findings.add(
              new HealthCheckFinding(
                  fund,
                  ISIN_MATCH,
                  FAIL,
                  "%s: ISIN %s not in model portfolio".formatted(fund, isin)));
        }
      }
      if (position.getQuantity() == null || position.getQuantity().compareTo(ZERO) == 0) {
        boolean inCurrentModelPortfolio = isin != null && currentIsins.contains(isin);
        boolean inPreviousModelPortfolio = isin != null && previousIsins.contains(isin);
        if (inCurrentModelPortfolio && inPreviousModelPortfolio) {
          findings.add(
              new HealthCheckFinding(
                  fund,
                  ISIN_MATCH,
                  FAIL,
                  "%s: quantity is %s for ISIN %s (held in model but position emptied?)"
                      .formatted(fund, position.getQuantity(), isin)));
        } else if (inPreviousModelPortfolio) {
          findings.add(
              new HealthCheckFinding(
                  fund,
                  ISIN_MATCH,
                  WARNING,
                  "%s: quantity is %s for ISIN %s (in-runoff — fully liquidated)"
                      .formatted(fund, position.getQuantity(), isin)));
        } else {
          findings.add(
              new HealthCheckFinding(
                  fund,
                  ISIN_MATCH,
                  WARNING,
                  "%s: quantity is %s for ISIN %s (newly added / not yet bought?)"
                      .formatted(fund, position.getQuantity(), isin)));
        }
      }
    }

    return findings;
  }
}
