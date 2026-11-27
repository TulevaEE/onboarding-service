package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.normalised;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record DeductedSale(
    String isin,
    @Nullable BigDecimal reportUnits,
    BigDecimal reportValue,
    BigDecimal unitsInSentSales,
    BigDecimal valueDeducted,
    DeductedBy deductedBy) {

  enum DeductedBy {
    UNITS,
    VALUE
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("isin", isin);
    putIfPresent(map, "reportUnits", reportUnits == null ? null : normalised(reportUnits));
    map.put("reportValue", normalised(reportValue));
    map.put("unitsInSentSales", normalised(unitsInSentSales));
    map.put("valueDeducted", normalised(valueDeducted));
    map.put("deductedBy", deductedBy.name());
    return map;
  }
}
