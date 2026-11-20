package ee.tuleva.onboarding.investment.check.rebalance;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

record NavWeights(
    LocalDate navDate, BigDecimal netAssetValue, Map<String, BigDecimal> marketValues) {

  private static final int WEIGHT_SCALE = 10;

  List<Deviation> outsideTolerance(Map<String, BigDecimal> modelWeights, BigDecimal tolerance) {
    return modelWeights.entrySet().stream()
        .map(model -> new Deviation(model.getKey(), model.getValue(), actualWeight(model.getKey())))
        .filter(deviation -> deviation.exceeds(tolerance))
        .sorted(Deviation.LARGEST_FIRST)
        .toList();
  }

  private BigDecimal actualWeight(String isin) {
    return marketValues.getOrDefault(isin, ZERO).divide(netAssetValue, WEIGHT_SCALE, HALF_UP);
  }
}
