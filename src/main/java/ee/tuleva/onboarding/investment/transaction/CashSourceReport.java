package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.plain;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;

import ee.tuleva.onboarding.investment.transaction.GatewayCash.CurrentDayBalance;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record CashSourceReport(
    CashSource source,
    GatewayCash gateway,
    @Nullable BigDecimal reportCash,
    List<CalculationWarning> warnings) {

  @Nullable BigDecimal gatewayMinusReportCash() {
    return gateway instanceof CurrentDayBalance current && reportCash != null
        ? current.balance().subtract(reportCash)
        : null;
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("source", source.name());
    putIfPresent(map, "reportCash", plain(reportCash));
    map.put("gateway", gateway.payload());
    putIfPresent(map, "gatewayMinusReportCash", plain(gatewayMinusReportCash()));
    return map;
  }
}
