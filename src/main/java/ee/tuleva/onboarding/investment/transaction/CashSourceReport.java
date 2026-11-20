package ee.tuleva.onboarding.investment.transaction;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record CashSourceReport(CashSource source, GatewayCash gateway) {

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("source", source.name());
    map.put("gateway", gateway.payload());
    return map;
  }
}
