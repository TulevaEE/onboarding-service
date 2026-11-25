package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.normalised;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record PriceValuation(
    String isin,
    Kind kind,
    @Nullable BigDecimal units,
    BigDecimal valueBefore,
    BigDecimal price,
    BigDecimal valueWithoutUnits,
    BigDecimal valueUsed) {

  enum Kind {
    MANUAL_PRICE("manualPriceValuations", "valueBeforeManualPrice", "manualPrice");

    private final String listKey;
    private final String valueBeforeKey;
    private final String priceKey;

    Kind(String listKey, String valueBeforeKey, String priceKey) {
      this.listKey = listKey;
      this.valueBeforeKey = valueBeforeKey;
      this.priceKey = priceKey;
    }

    String listKey() {
      return listKey;
    }
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("isin", isin);
    putIfPresent(map, "units", units == null ? null : normalised(units));
    map.put(kind.valueBeforeKey, normalised(valueBefore));
    map.put(kind.priceKey, normalised(price));
    if (valueWithoutUnits.signum() != 0) {
      map.put("valueWithoutUnits", normalised(valueWithoutUnits));
    }
    map.put("valueUsed", normalised(valueUsed));
    map.put("valuedAt", units == null ? "WITHOUT_UNITS" : kind.name());
    return map;
  }
}
