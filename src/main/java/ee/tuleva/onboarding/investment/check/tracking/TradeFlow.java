package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.SecurityQuantities;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

record TradeFlow(BigDecimal atMark, List<String> unpricedIsins) {

  private static final int EUR_SCALE = 2;

  static TradeFlow none() {
    return new TradeFlow(ZERO.setScale(EUR_SCALE), List.of());
  }

  static TradeFlow between(
      List<SecurityData> securities,
      List<FundPosition> previousPositions,
      List<FundPosition> todayPositions) {
    var today = SecurityQuantities.byIsin(todayPositions);
    var previous = SecurityQuantities.byIsin(previousPositions);
    var marks = marks(securities, previousPositions, todayPositions);

    var traded =
        Stream.concat(today.keySet().stream(), previous.keySet().stream())
            .distinct()
            .filter(isin -> quantityChange(today, previous, isin).signum() != 0)
            .sorted()
            .toList();

    var atMark =
        traded.stream()
            .filter(marks::containsKey)
            .map(isin -> quantityChange(today, previous, isin).multiply(marks.get(isin)))
            .reduce(ZERO, BigDecimal::add)
            .setScale(EUR_SCALE, HALF_UP);

    return new TradeFlow(atMark, traded.stream().filter(isin -> !marks.containsKey(isin)).toList());
  }

  boolean isComplete() {
    return unpricedIsins.isEmpty();
  }

  private static BigDecimal quantityChange(
      Map<String, BigDecimal> today, Map<String, BigDecimal> previous, String isin) {
    return today.getOrDefault(isin, ZERO).subtract(previous.getOrDefault(isin, ZERO));
  }

  private static Map<String, BigDecimal> marks(
      List<SecurityData> securities,
      List<FundPosition> previousPositions,
      List<FundPosition> todayPositions) {
    var marks = new LinkedHashMap<String, BigDecimal>();
    securities.stream()
        .filter(security -> security.today().price() != null)
        .forEach(
            security ->
                marks.putIfAbsent(security.isin(), security.today().requirePrice(security.isin())));
    reportedPrices(todayPositions).forEach(marks::putIfAbsent);
    reportedPrices(previousPositions).forEach(marks::putIfAbsent);
    return marks;
  }

  private static Map<String, BigDecimal> reportedPrices(List<FundPosition> positions) {
    var prices = new LinkedHashMap<String, BigDecimal>();
    for (var position : positions) {
      var isin = position.getAccountId();
      var price = position.getMarketPrice();
      if (position.getAccountType() == SECURITY
          && isin != null
          && price != null
          && price.signum() != 0) {
        prices.putIfAbsent(isin, price);
      }
    }
    return prices;
  }
}
