package ee.tuleva.onboarding.investment.transaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record PositionAdjustments(
    List<DeductedSale> deductedSales, List<ManualPriceValuation> manualPriceValuations) {

  static final PositionAdjustments NONE = new PositionAdjustments(List.of(), List.of());

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (!deductedSales.isEmpty()) {
      map.put("deductedSales", deductedSales.stream().map(DeductedSale::payload).toList());
    }
    if (!manualPriceValuations.isEmpty()) {
      map.put(
          "manualPriceValuations",
          manualPriceValuations.stream().map(ManualPriceValuation::payload).toList());
    }
    return map;
  }
}
