package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.ledger.RegistrarPayoutReason;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record R16TypeForecast(
    RegistrarPayoutReason type, BigDecimal units, BigDecimal eur, Status status) {

  enum Status {
    NOTHING_FORECAST,
    BOOKED,
    NOT_BOOKED
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("type", type.name());
    map.put("units", units.toPlainString());
    map.put("eur", eur.toPlainString());
    map.put("status", status.name());
    return map;
  }

  boolean stillToPay() {
    return status == Status.NOT_BOOKED;
  }
}
