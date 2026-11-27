package ee.tuleva.onboarding.investment.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.NullMarked;

@NullMarked
record PositionAdjustments(List<DeductedSale> deductedSales, List<PriceValuation> priceValuations) {

  static final PositionAdjustments NONE = new PositionAdjustments(List.of(), List.of());

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (!deductedSales.isEmpty()) {
      map.put("deductedSales", deductedSales.stream().map(DeductedSale::payload).toList());
    }
    priceValuations.stream()
        .collect(
            groupingBy(
                PriceValuation::kind, TreeMap::new, mapping(PriceValuation::payload, toList())))
        .forEach((kind, payloads) -> map.put(kind.listKey(), payloads));
    return map;
  }
}
