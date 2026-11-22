package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.normalised;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record ManualPriceValuation(
    String isin,
    @Nullable BigDecimal units,
    BigDecimal valueBeforeManualPrice,
    BigDecimal manualPrice,
    BigDecimal valueWithoutUnits,
    BigDecimal valueUsed) {

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("isin", isin);
    putIfPresent(map, "units", units == null ? null : normalised(units));
    map.put("valueBeforeManualPrice", normalised(valueBeforeManualPrice));
    map.put("manualPrice", normalised(manualPrice));
    if (valueWithoutUnits.signum() != 0) {
      map.put("valueWithoutUnits", normalised(valueWithoutUnits));
    }
    map.put("valueUsed", normalised(valueUsed));
    map.put("valuedAt", units == null ? "WITHOUT_UNITS" : "MANUAL_PRICE");
    return map;
  }
}
