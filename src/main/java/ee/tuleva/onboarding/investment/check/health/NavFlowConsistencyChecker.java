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

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.SecurityQuantities;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NavFlowConsistencyChecker {

  private final MarketPnlCalculator marketPnlCalculator;

  private static final int SCALE = 8;
  private static final int EUR_SCALE = 2;
  private static final List<AccountType> NET_ASSET_TYPES =
      List.of(SECURITY, CASH, RECEIVABLES, LIABILITY);

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> todayPositions,
      List<FundPosition> previousPositions,
      @Nullable BigDecimal threshold,
      Map<String, ExitMark> exitMarks) {
    var openingNetAssets = netAssets(previousPositions);
    if (isSilent(previousPositions, todayPositions, openingNetAssets)) {
      return List.of();
    }

    var marketPnl = marketPnlCalculator.mark(previousPositions, todayPositions, exitMarks);
    var notRunReason = notRunReason(previousPositions, todayPositions, marketPnl);
    if (notRunReason.isPresent()) {
      return List.of(couldNotRun(fund, notRunReason.get()));
    }

    if (threshold == null) {
      return List.of(couldNotRun(fund, "NAV_FLOW_CONSISTENCY_THRESHOLD is not configured yet"));
    }

    var closingNetAssets = netAssets(todayPositions);
    var todayUnits = outstandingUnits(todayPositions).orElseThrow();
    var previousUnits = outstandingUnits(previousPositions).orElseThrow();

    var unitsChange = todayUnits.subtract(previousUnits);
    var navPerUnit = closingNetAssets.divide(todayUnits, SCALE, HALF_UP);
    var unitFlow = unitsChange.multiply(navPerUnit);
    var unexplained =
        closingNetAssets
            .subtract(openingNetAssets)
            .subtract(marketPnl.amount())
            .subtract(unitFlow)
            .setScale(EUR_SCALE, HALF_UP);
    var fraction = unexplained.divide(openingNetAssets, SCALE, HALF_UP);

    if (!marketPnl.exitLegs().isEmpty()) {
      log.info(
          "NAV flow marked an exited holding at its executed price: fund={}, exitLegs={}",
          fund,
          ExitLeg.describeAll(marketPnl.exitLegs()));
    }

    if (fraction.abs().compareTo(threshold) < 0) {
      return List.of();
    }

    return List.of(
        new HealthCheckFinding(
            fund,
            NAV_FLOW_CONSISTENCY,
            WARNING,
            ("NAV flow does not reconcile: unexplained=%s EUR, fraction=%s, marketPnl=%s,"
                    + " unitFlow=%s, unitsChange=%s, quantitiesChanged=%s%s"
                    + " (the SEB report carries no fees, so a small residual is expected)")
                .formatted(
                    unexplained.toPlainString(),
                    fraction.toPlainString(),
                    marketPnl.amount().setScale(EUR_SCALE, HALF_UP).toPlainString(),
                    unitFlow.setScale(EUR_SCALE, HALF_UP).toPlainString(),
                    unitsChange.toPlainString(),
                    SecurityQuantities.changedBetween(previousPositions, todayPositions),
                    marketPnl.exitLegs().isEmpty()
                        ? ""
                        : ", exitLegs=[%s]".formatted(ExitLeg.describeAll(marketPnl.exitLegs())))));
  }

  private boolean isSilent(
      List<FundPosition> previousPositions,
      List<FundPosition> todayPositions,
      BigDecimal openingNetAssets) {
    if (previousPositions.isEmpty() || openingNetAssets.signum() <= 0) {
      return true;
    }
    var todayUnits = outstandingUnits(todayPositions);
    return todayUnits.isEmpty() || todayUnits.get().signum() <= 0;
  }

  private Optional<String> notRunReason(
      List<FundPosition> previousPositions,
      List<FundPosition> todayPositions,
      MarketPnl marketPnl) {
    if (outstandingUnits(previousPositions).isEmpty()) {
      return Optional.of(
          "the previous day's report carries no outstanding units to reconcile against");
    }
    var unmarkableAccounts = unmarkableAccounts(previousPositions, todayPositions);
    if (!unmarkableAccounts.isEmpty()) {
      return Optional.of(
          "valued securities without an ISIN cannot be marked to market, unmarkableAccounts=%s"
              .formatted(String.join(",", unmarkableAccounts)));
    }
    var conflictinglyPriced =
        conflictinglyPricedHoldings(
            SecurityQuantities.byIsin(previousPositions).keySet(),
            previousPositions,
            todayPositions);
    if (!conflictinglyPriced.isEmpty()) {
      return Optional.of(
          "the same holding is priced two ways within one report, conflictinglyPriced=%s"
              .formatted(String.join(",", conflictinglyPriced)));
    }
    if (!marketPnl.unexplainedExits().isEmpty()) {
      return Optional.of(
          ("holdings left the report with no execution to price the exit, so the line may be a"
                  + " truncated report rather than a trade, unexplainedExits=%s")
              .formatted(String.join(",", marketPnl.unexplainedExits())));
    }
    if (!marketPnl.isComplete()) {
      return Optional.of(
          ("holdings priced on only one of the two days cannot be marked to market,"
                  + " unpricedHoldings=%s")
              .formatted(String.join(",", marketPnl.unpricedHoldings())));
    }
    return Optional.empty();
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

  private List<String> unmarkableAccounts(
      List<FundPosition> previousPositions, List<FundPosition> todayPositions) {
    return Stream.concat(
            securities(previousPositions).stream(), securities(todayPositions).stream())
        .filter(position -> position.getAccountId() == null)
        .filter(NavFlowConsistencyChecker::carriesValue)
        .map(FundPosition::getAccountName)
        .distinct()
        .sorted()
        .toList();
  }

  private List<String> conflictinglyPricedHoldings(
      Set<String> markedIsins,
      List<FundPosition> previousPositions,
      List<FundPosition> todayPositions) {
    return Stream.of(previousPositions, todayPositions)
        .flatMap(positions -> conflictinglyPricedHoldings(markedIsins, positions).stream())
        .distinct()
        .sorted()
        .toList();
  }

  private List<String> conflictinglyPricedHoldings(
      Set<String> markedIsins, List<FundPosition> positions) {
    Map<String, Set<BigDecimal>> pricesByIsin = new TreeMap<>();
    for (var position : securities(positions)) {
      var isin = position.getAccountId();
      var marketPrice = position.getMarketPrice();
      if (isin != null && marketPrice != null && markedIsins.contains(isin)) {
        pricesByIsin.computeIfAbsent(isin, key -> new TreeSet<>()).add(marketPrice);
      }
    }
    return pricesByIsin.entrySet().stream()
        .filter(entry -> entry.getValue().size() > 1)
        .map(Map.Entry::getKey)
        .toList();
  }

  private static boolean carriesValue(FundPosition position) {
    var marketValue = position.getMarketValue();
    return marketValue != null && marketValue.signum() != 0;
  }

  private HealthCheckFinding couldNotRun(TulevaFund fund, String reason) {
    return new HealthCheckFinding(
        fund,
        NAV_FLOW_CONSISTENCY,
        NOT_RUN,
        "NAV flow could not be reconciled: %s".formatted(reason));
  }

  private List<FundPosition> securities(List<FundPosition> positions) {
    return positions.stream().filter(position -> position.getAccountType() == SECURITY).toList();
  }
}
