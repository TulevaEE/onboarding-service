package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.RECEIVABLES;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class ReceivablesChecker {

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> todaySecurities,
      List<FundPosition> previousSecurities,
      List<FundPosition> receivables) {
    if (previousSecurities.isEmpty()) {
      return List.of();
    }

    Map<String, BigDecimal> previousByIsin =
        previousSecurities.stream()
            .filter(p -> p.getAccountId() != null && p.getQuantity() != null)
            .collect(Collectors.toMap(FundPosition::getAccountId, FundPosition::getQuantity));

    boolean anyDecreased =
        todaySecurities.stream()
            .anyMatch(
                p -> {
                  BigDecimal prev = previousByIsin.get(p.getAccountId());
                  return prev != null
                      && p.getQuantity() != null
                      && p.getQuantity().compareTo(prev) < 0;
                });

    if (!anyDecreased) {
      return List.of();
    }

    boolean hasReceivables =
        receivables.stream()
            .anyMatch(
                r -> r.getMarketValue() != null && r.getMarketValue().abs().compareTo(ZERO) > 0);

    if (!hasReceivables) {
      return List.of(
          new HealthCheckFinding(
              fund,
              RECEIVABLES,
              FAIL,
              "%s: SECURITY quantities decreased but no receivables found".formatted(fund)));
    }

    return List.of();
  }
}
