package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.plain;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record R16Reserve(
    Basis basis,
    List<R16TypeForecast> types,
    @Nullable BigDecimal reserveSoft,
    @Nullable BigDecimal reserveHard,
    BigDecimal reserved) {

  enum Basis {
    NO_R16_THIS_MONTH,
    COVERED_BY_R45,
    FORECAST_NOT_YET_BOOKED
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("basis", basis.name());
    map.put("types", types.stream().map(R16TypeForecast::payload).toList());
    putIfPresent(map, "reserveSoft", plain(reserveSoft));
    putIfPresent(map, "reserveHard", plain(reserveHard));
    map.put("reserved", reserved.toPlainString());
    return map;
  }

  static final R16Reserve NONE =
      new R16Reserve(Basis.NO_R16_THIS_MONTH, List.of(), null, null, ZERO);
}
