package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.normalised;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record SizedSale(
    String isin,
    @Nullable BigDecimal reportUnits,
    BigDecimal unitsInSentSales,
    BigDecimal quantitySold,
    BigDecimal price,
    BigDecimal engineAmount,
    BigDecimal orderAmount,
    @Nullable Kind kind) {

  enum Kind {
    FULL,
    PARTIAL
  }

  boolean dropped() {
    return quantitySold.signum() == 0;
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("isin", isin);
    putIfPresent(map, "reportUnits", reportUnits == null ? null : normalised(reportUnits));
    map.put("unitsInSentSales", normalised(unitsInSentSales));
    map.put("quantitySold", normalised(quantitySold));
    map.put("price", normalised(price));
    map.put("engineAmount", normalised(engineAmount));
    map.put("orderAmount", normalised(orderAmount));
    if (kind != null) {
      map.put("fullSale", kind == Kind.FULL);
    }
    return map;
  }
}
