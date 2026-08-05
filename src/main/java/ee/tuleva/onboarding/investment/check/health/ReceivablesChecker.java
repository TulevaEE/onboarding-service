package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
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

  private static final int MAX_ISINS_IN_MESSAGE = 10;

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> todaySecurities,
      List<FundPosition> previousSecurities,
      List<FundPosition> receivables,
      List<FundPosition> previousReceivables) {
    if (previousSecurities.isEmpty()) {
      return List.of();
    }

    Map<String, BigDecimal> previousByIsin = SecurityQuantities.byIsin(previousSecurities);

    var decreased =
        SecurityQuantities.byIsin(todaySecurities).entrySet().stream()
            .filter(entry -> previousByIsin.containsKey(entry.getKey()))
            .filter(entry -> entry.getValue().compareTo(previousByIsin.get(entry.getKey())) < 0)
            .sorted(Map.Entry.comparingByKey())
            .toList();

    if (decreased.isEmpty()) {
      return List.of();
    }

    if (hasNonZero(receivables) || hasNonZero(previousReceivables)) {
      return List.of();
    }

    return List.of(
        new HealthCheckFinding(
            fund,
            RECEIVABLES,
            WARNING,
            "SECURITY qty decreased without matching receivables — %s"
                .formatted(formatDeltas(decreased, previousByIsin))));
  }

  private boolean hasNonZero(List<FundPosition> positions) {
    return positions.stream()
        .anyMatch(p -> p.getMarketValue() != null && p.getMarketValue().abs().compareTo(ZERO) > 0);
  }

  private String formatDeltas(
      List<Map.Entry<String, BigDecimal>> decreased, Map<String, BigDecimal> previousByIsin) {
    int total = decreased.size();
    var shown =
        decreased.stream()
            .limit(MAX_ISINS_IN_MESSAGE)
            .map(
                entry ->
                    "%s (%s)"
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
