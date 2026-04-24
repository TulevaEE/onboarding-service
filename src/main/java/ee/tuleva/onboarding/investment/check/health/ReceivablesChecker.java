package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.RECEIVABLES;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.util.Comparator;
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

    Map<String, BigDecimal> previousByIsin =
        previousSecurities.stream()
            .filter(p -> p.getAccountId() != null && p.getQuantity() != null)
            .collect(Collectors.toMap(FundPosition::getAccountId, FundPosition::getQuantity));

    var decreased =
        todaySecurities.stream()
            .filter(
                p -> {
                  BigDecimal prev = previousByIsin.get(p.getAccountId());
                  return prev != null
                      && p.getQuantity() != null
                      && p.getQuantity().compareTo(prev) < 0;
                })
            .sorted(Comparator.comparing(FundPosition::getAccountId))
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
      List<FundPosition> decreased, Map<String, BigDecimal> previousByIsin) {
    int total = decreased.size();
    var shown =
        decreased.stream()
            .limit(MAX_ISINS_IN_MESSAGE)
            .map(
                p ->
                    "%s (%s)"
                        .formatted(
                            p.getAccountId(),
                            p.getQuantity()
                                .subtract(previousByIsin.get(p.getAccountId()))
                                .toPlainString()))
            .collect(Collectors.joining(", "));
    if (total > MAX_ISINS_IN_MESSAGE) {
      return shown + ", ... (%d more)".formatted(total - MAX_ISINS_IN_MESSAGE);
    }
    return shown;
  }
}
