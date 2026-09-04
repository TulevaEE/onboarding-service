package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.NAV_FLOW_CONSISTENCY;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.investment.position.AccountType.UNITS;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.SecurityQuantities;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
class NavFlowConsistencyChecker {

  private static final int SCALE = 8;
  private static final int EUR_SCALE = 2;
  private static final List<AccountType> NET_ASSET_TYPES =
      List.of(SECURITY, CASH, RECEIVABLES, LIABILITY);

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
    if (previousUnits.isEmpty() || todayUnits.isEmpty() || todayUnits.get().signum() <= 0) {
      return List.of();
    }

    var marketPnl = marketPnl(previousPositions, todayPositions);
    if (!marketPnl.isComplete()) {
      return List.of(couldNotRun(fund, marketPnl.unpricedHoldings()));
    }

    var unitsChange = todayUnits.get().subtract(previousUnits.get());
    var navPerUnit = closingNetAssets.divide(todayUnits.get(), SCALE, HALF_UP);
    var unitFlow = unitsChange.multiply(navPerUnit);
    var unexplained =
        closingNetAssets
            .subtract(openingNetAssets)
            .subtract(marketPnl.amount())
            .subtract(unitFlow)
            .setScale(EUR_SCALE, HALF_UP);
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
                    marketPnl.amount().setScale(EUR_SCALE, HALF_UP).toPlainString(),
                    unitFlow.setScale(EUR_SCALE, HALF_UP).toPlainString(),
                    unitsChange.toPlainString(),
                    SecurityQuantities.changedBetween(previousPositions, todayPositions))));
  }

  private BigDecimal netAssets(List<FundPosition> positions) {
    return positions.stream()
        .filter(position -> NET_ASSET_TYPES.contains(position.getAccountType()))
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private Optional<BigDecimal> outstandingUnits(List<FundPosition> positions) {
    return positions.stream()
        .filter(position -> position.getAccountType() == UNITS)
        .map(FundPosition::getQuantity)
        .filter(Objects::nonNull)
        .findFirst();
  }

  private MarketPnl marketPnl(
      List<FundPosition> previousPositions, List<FundPosition> todayPositions) {
    var todayPrices = pricesByIsin(todayPositions);
    var previousPrices = pricesByIsin(previousPositions);

    var amount = ZERO;
    var unpricedHoldings = new TreeSet<String>();
    for (var holding : SecurityQuantities.byIsin(previousPositions).entrySet()) {
      var todayPrice = todayPrices.get(holding.getKey());
      var previousPrice = previousPrices.get(holding.getKey());
      if (todayPrice == null || previousPrice == null) {
        unpricedHoldings.add(holding.getKey());
      } else {
        amount = amount.add(holding.getValue().multiply(todayPrice.subtract(previousPrice)));
      }
    }
    return new MarketPnl(amount, List.copyOf(unpricedHoldings));
  }

  private record MarketPnl(BigDecimal amount, List<String> unpricedHoldings) {
    private boolean isComplete() {
      return unpricedHoldings.isEmpty();
    }
  }

  private HealthCheckFinding couldNotRun(TulevaFund fund, List<String> unpricedHoldings) {
    return new HealthCheckFinding(
        fund,
        NAV_FLOW_CONSISTENCY,
        NOT_RUN,
        ("NAV flow could not be reconciled: holdings priced on only one of the two days"
                + " cannot be marked to market, unpricedHoldings=%s")
            .formatted(String.join(",", unpricedHoldings)));
  }

  private Map<String, BigDecimal> pricesByIsin(List<FundPosition> positions) {
    return securities(positions).stream()
        .filter(position -> position.getAccountId() != null && position.getMarketPrice() != null)
        .collect(
            toMap(
                FundPosition::getAccountId,
                FundPosition::getMarketPrice,
                (first, second) -> first));
  }

  private List<FundPosition> securities(List<FundPosition> positions) {
    return positions.stream().filter(position -> position.getAccountType() == SECURITY).toList();
  }
}
