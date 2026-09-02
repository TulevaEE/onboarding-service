package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.NAV_FLOW_CONSISTENCY;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.investment.position.AccountType.UNITS;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class NavFlowConsistencyChecker {

  private static final int SCALE = 8;

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> todayPositions,
      List<FundPosition> previousPositions,
      BigDecimal threshold) {
    if (previousPositions.isEmpty()) {
      return List.of();
    }

    var openingNetAssets = netAssets(previousPositions);
    var closingNetAssets = netAssets(todayPositions);
    if (openingNetAssets.signum() <= 0) {
      return List.of();
    }

    var previousUnits = outstandingUnits(previousPositions);
    var todayUnits = outstandingUnits(todayPositions);
    if (previousUnits == null || todayUnits == null || todayUnits.signum() <= 0) {
      return List.of();
    }

    var marketPnl = marketPnl(previousPositions, todayPositions);
    if (marketPnl == null) {
      return List.of();
    }

    var unitsChange = todayUnits.subtract(previousUnits);
    var navPerUnit = closingNetAssets.divide(todayUnits, SCALE, HALF_UP);
    var unitFlow = unitsChange.multiply(navPerUnit);
    var unexplained =
        closingNetAssets
            .subtract(openingNetAssets)
            .subtract(marketPnl)
            .subtract(unitFlow)
            .setScale(2, HALF_UP);
    var fraction = unexplained.divide(openingNetAssets, SCALE, HALF_UP);

    if (fraction.abs().compareTo(threshold) < 0) {
      return List.of();
    }

    return List.of(
        new HealthCheckFinding(
            fund,
            NAV_FLOW_CONSISTENCY,
            WARNING,
            ("NAV flow does not reconcile: unexplained=%s EUR, fraction=%s, marketPnl=%s,"
                    + " unitFlow=%s, unitsChange=%s, quantitiesChanged=%s"
                    + " (the SEB report carries no fees, so a small residual is expected)")
                .formatted(
                    unexplained.toPlainString(),
                    fraction.toPlainString(),
                    marketPnl.setScale(2, HALF_UP).toPlainString(),
                    unitFlow.setScale(2, HALF_UP).toPlainString(),
                    unitsChange.toPlainString(),
                    quantitiesChanged(previousPositions, todayPositions))));
  }

  private BigDecimal netAssets(List<FundPosition> positions) {
    return positions.stream()
        .filter(position -> position.getAccountType() != UNITS)
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private @Nullable BigDecimal outstandingUnits(List<FundPosition> positions) {
    return positions.stream()
        .filter(position -> position.getAccountType() == UNITS)
        .map(FundPosition::getQuantity)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private @Nullable BigDecimal marketPnl(
      List<FundPosition> previousPositions, List<FundPosition> todayPositions) {
    var todayPrices = pricesByIsin(todayPositions);
    var previousPrices = pricesByIsin(previousPositions);
    var previousQuantities = quantitiesByIsin(previousPositions);

    var pnl = ZERO;
    for (var holding : previousQuantities.entrySet()) {
      var todayPrice = todayPrices.get(holding.getKey());
      var previousPrice = previousPrices.get(holding.getKey());
      if (todayPrice == null || previousPrice == null) {
        return null;
      }
      pnl = pnl.add(holding.getValue().multiply(todayPrice.subtract(previousPrice)));
    }
    return pnl;
  }

  private boolean quantitiesChanged(
      List<FundPosition> previousPositions, List<FundPosition> todayPositions) {
    var previous = quantitiesByIsin(previousPositions);
    var today = quantitiesByIsin(todayPositions);
    if (!previous.keySet().equals(today.keySet())) {
      return true;
    }
    return previous.entrySet().stream()
        .anyMatch(entry -> entry.getValue().compareTo(today.get(entry.getKey())) != 0);
  }

  private Map<String, BigDecimal> quantitiesByIsin(List<FundPosition> positions) {
    return SecurityQuantities.byIsin(securities(positions));
  }

  private Map<String, BigDecimal> pricesByIsin(List<FundPosition> positions) {
    return securities(positions).stream()
        .filter(position -> position.getAccountId() != null && position.getMarketPrice() != null)
        .collect(
            Collectors.toMap(
                FundPosition::getAccountId,
                FundPosition::getMarketPrice,
                (first, second) -> first));
  }

  private List<FundPosition> securities(List<FundPosition> positions) {
    return positions.stream().filter(position -> position.getAccountType() == SECURITY).toList();
  }
}
