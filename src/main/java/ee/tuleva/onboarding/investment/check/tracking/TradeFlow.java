package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.SecurityQuantities;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

final class TradeFlow {

  private static final int EUR_SCALE = 2;

  private TradeFlow() {}

  static BigDecimal atMark(
      List<SecurityData> securities,
      List<FundPosition> previousPositions,
      List<FundPosition> todayPositions) {
    var today = SecurityQuantities.byIsin(todayPositions);
    var previous = SecurityQuantities.byIsin(previousPositions);
    var marks =
        securities.stream()
            .filter(security -> security.today().price() != null)
            .collect(
                toMap(
                    SecurityData::isin,
                    security -> security.today().requirePrice(security.isin()),
                    (first, second) -> first));

    return Stream.concat(today.keySet().stream(), previous.keySet().stream())
        .distinct()
        .filter(marks::containsKey)
        .map(
            isin ->
                today
                    .getOrDefault(isin, ZERO)
                    .subtract(previous.getOrDefault(isin, ZERO))
                    .multiply(marks.get(isin)))
        .reduce(ZERO, BigDecimal::add)
        .setScale(EUR_SCALE, HALF_UP);
  }
}
