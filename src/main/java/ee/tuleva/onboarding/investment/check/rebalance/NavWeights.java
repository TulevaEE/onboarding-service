package ee.tuleva.onboarding.investment.check.rebalance;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

record NavWeights(
    LocalDate navDate, BigDecimal netAssetValue, Map<String, BigDecimal> marketValues) {

  private static final int WEIGHT_SCALE = 10;

  List<Deviation> outsideTolerance(Map<String, BigDecimal> modelWeights, BigDecimal tolerance) {
    return Stream.concat(modelWeights.keySet().stream(), marketValues.keySet().stream())
        .distinct()
        .map(isin -> new Deviation(isin, modelWeights.getOrDefault(isin, ZERO), actualWeight(isin)))
        .filter(deviation -> deviation.exceeds(tolerance))
        .sorted(Deviation.LARGEST_FIRST)
        .toList();
  }

  private BigDecimal actualWeight(String isin) {
    return marketValues.getOrDefault(isin, ZERO).divide(netAssetValue, WEIGHT_SCALE, HALF_UP);
  }
}
