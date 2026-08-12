package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.QUANTITY_CHANGE;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
class QuantityChangeChecker {

  private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.01");

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> securities,
      List<FundPosition> previousSecurities,
      Map<String, TradedQuantity> tradedQuantities) {
    if (previousSecurities.isEmpty()) {
      return List.of();
    }

    Map<String, BigDecimal> currentByIsin = SecurityQuantities.byIsin(securities);
    Map<String, BigDecimal> previousByIsin = SecurityQuantities.byIsin(previousSecurities);
    Set<String> reportedWithoutQuantity =
        Stream.concat(
                isinsWithoutQuantity(securities, currentByIsin),
                isinsWithoutQuantity(previousSecurities, previousByIsin))
            .collect(Collectors.toSet());

    Set<String> isins = new TreeSet<>(previousByIsin.keySet());
    isins.addAll(currentByIsin.keySet());

    return isins.stream()
        .filter(isin -> !reportedWithoutQuantity.contains(isin))
        .map(
            isin ->
                unexplainedChange(
                    fund,
                    isin,
                    previousByIsin.getOrDefault(isin, ZERO),
                    currentByIsin.getOrDefault(isin, ZERO),
                    tradedQuantities.getOrDefault(isin, TradedQuantity.NONE)))
        .filter(Objects::nonNull)
        .toList();
  }

  private Stream<String> isinsWithoutQuantity(
      List<FundPosition> positions, Map<String, BigDecimal> quantifiedByIsin) {
    return positions.stream()
        .map(FundPosition::getAccountId)
        .filter(Objects::nonNull)
        .filter(isin -> !quantifiedByIsin.containsKey(isin));
  }

  private HealthCheckFinding unexplainedChange(
      TulevaFund fund,
      String isin,
      BigDecimal previousQuantity,
      BigDecimal currentQuantity,
      TradedQuantity traded) {
    BigDecimal quantityChange = currentQuantity.subtract(previousQuantity);
    BigDecimal expectedChange = traded.netChange();
    if (quantityChange.subtract(expectedChange).abs().compareTo(ROUNDING_TOLERANCE) < 0) {
      return null;
    }

    return new HealthCheckFinding(
        fund,
        QUANTITY_CHANGE,
        WARNING,
        "%s: SECURITY quantity changed by %s for ISIN %s (previous %s, current %s) — executed transactions explain %s"
            .formatted(
                fund,
                quantityChange.toPlainString(),
                isin,
                previousQuantity.toPlainString(),
                currentQuantity.toPlainString(),
                expectedChange.toPlainString()));
  }
}
