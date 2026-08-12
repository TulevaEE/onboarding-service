package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
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

  private static final int MAX_ISINS_IN_MESSAGE = 10;

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> todaySecurities,
      List<FundPosition> previousSecurities,
      List<FundPosition> liabilities,
      List<FundPosition> previousLiabilities) {
    if (previousSecurities.isEmpty()) {
      return List.of();
    }

    Map<String, BigDecimal> previousByIsin = SecurityQuantities.byIsin(previousSecurities);

    var increased =
        SecurityQuantities.byIsin(todaySecurities).entrySet().stream()
            .filter(entry -> previousByIsin.containsKey(entry.getKey()))
            .filter(entry -> entry.getValue().compareTo(previousByIsin.get(entry.getKey())) > 0)
            .sorted(Map.Entry.comparingByKey())
            .toList();

    if (increased.isEmpty()) {
      return List.of();
    }

    if (hasNonZero(liabilities) || hasNonZero(previousLiabilities)) {
      return List.of();
    }

    return List.of(
        new HealthCheckFinding(
            fund,
            PAYABLES,
            WARNING,
            "SECURITY qty increased without matching payables — %s"
                .formatted(formatDeltas(increased, previousByIsin))));
  }

  private boolean hasNonZero(List<FundPosition> positions) {
    return positions.stream()
        .anyMatch(p -> p.getMarketValue() != null && p.getMarketValue().abs().compareTo(ZERO) > 0);
  }

  private String formatDeltas(
      List<Map.Entry<String, BigDecimal>> increased, Map<String, BigDecimal> previousByIsin) {
    int total = increased.size();
    var shown =
        increased.stream()
            .limit(MAX_ISINS_IN_MESSAGE)
            .map(
                entry ->
                    "%s (+%s)"
                        .formatted(
                            entry.getKey(),
                            entry
                                .getValue()
                                .subtract(previousByIsin.get(entry.getKey()))
                                .toPlainString()))
            .collect(Collectors.joining(", "));
    if (total > MAX_ISINS_IN_MESSAGE) {
      return shown + ", ... (%d more)".formatted(total - MAX_ISINS_IN_MESSAGE);
    }
    return shown;
  }
}
