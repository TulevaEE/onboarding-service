package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.SecurityQuantities;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class MarketPnlCalculator {

  MarketPnl mark(
      List<FundPosition> previousPositions,
      List<FundPosition> todayPositions,
      Map<String, ExitMark> exitMarks) {
    var todayPrices = pricesByIsin(todayPositions);
    var previousPrices = pricesByIsin(previousPositions);
    var todayIsins = SecurityQuantities.byIsin(todayPositions).keySet();

    var amount = ZERO;
    var unpricedHoldings = new TreeSet<String>();
    var unexplainedExits = new TreeSet<String>();
    var exitLegs = new ArrayList<ExitLeg>();
    for (var holding : heldQuantities(previousPositions).entrySet()) {
      var isin = holding.getKey();
      var quantity = holding.getValue();
      var previousPrice = previousPrices.get(isin);
      var todayPrice = todayPrices.get(isin);
      var exitMark = todayPrice == null ? exitMarks.get(isin) : null;

      if (previousPrice == null || (todayPrice == null && exitMark == null)) {
        if (isUnexplainedExit(isin, exitMark, todayIsins)) {
          unexplainedExits.add(isin);
        } else {
          unpricedHoldings.add(isin);
        }
      } else if (todayPrice != null) {
        amount = amount.add(quantity.multiply(todayPrice.subtract(previousPrice)));
      } else if (exitMark != null) {
        var exitLeg = new ExitLeg(isin, quantity, previousPrice, exitMark);
        exitLegs.add(exitLeg);
        amount = amount.add(exitLeg.marketEffect());
      }
    }
    return new MarketPnl(
        amount,
        List.copyOf(unpricedHoldings),
        List.copyOf(unexplainedExits),
        List.copyOf(exitLegs));
  }

  private static Map<String, BigDecimal> heldQuantities(List<FundPosition> positions) {
    return SecurityQuantities.byIsin(positions).entrySet().stream()
        .filter(holding -> holding.getValue().signum() != 0)
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static boolean isUnexplainedExit(
      String isin, @Nullable ExitMark exitMark, Set<String> todayIsins) {
    return exitMark == null && !todayIsins.contains(isin);
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
