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
    BigDecimal reportValue,
    BigDecimal manualPrice,
    BigDecimal valueUsed) {

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("isin", isin);
    putIfPresent(map, "units", units == null ? null : normalised(units));
    map.put("reportValue", normalised(reportValue));
    map.put("manualPrice", normalised(manualPrice));
    map.put("valueUsed", normalised(valueUsed));
    map.put("valuedAt", units == null ? "REPORT_VALUE" : "MANUAL_PRICE");
    return map;
  }
}
