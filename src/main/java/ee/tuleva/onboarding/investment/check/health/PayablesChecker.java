package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.PAYABLES;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class PayablesChecker {

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> todaySecurities,
      List<FundPosition> previousSecurities,
      List<FundPosition> liabilities) {
    if (previousSecurities.isEmpty()) {
      return List.of();
    }

    Map<String, BigDecimal> previousByIsin =
        previousSecurities.stream()
            .filter(p -> p.getAccountId() != null && p.getQuantity() != null)
            .collect(Collectors.toMap(FundPosition::getAccountId, FundPosition::getQuantity));

    boolean anyIncreased =
        todaySecurities.stream()
            .anyMatch(
                p -> {
                  BigDecimal prev = previousByIsin.get(p.getAccountId());
                  return prev != null
                      && p.getQuantity() != null
                      && p.getQuantity().compareTo(prev) > 0;
                });

    if (!anyIncreased) {
      return List.of();
    }

    boolean hasLiabilities =
        liabilities.stream()
            .anyMatch(
                l -> l.getMarketValue() != null && l.getMarketValue().abs().compareTo(ZERO) > 0);

    if (!hasLiabilities) {
      return List.of(
          new HealthCheckFinding(
              fund,
              PAYABLES,
              FAIL,
              "%s: SECURITY quantities increased but no payables found".formatted(fund)));
    }

    return List.of();
  }
}
