package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.plain;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;

import ee.tuleva.onboarding.investment.transaction.GatewayCash.CurrentDayBalance;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record CashSourceReport(
    CashSource source,
    GatewayCash gateway,
    @Nullable BigDecimal reportCash,
    List<CalculationWarning> warnings,
    GatewayHold hold) {

  Optional<CurrentDayBalance> gatewayBalanceInUse() {
    return source == CashSource.GATEWAY_BALANCE && gateway instanceof CurrentDayBalance current
        ? Optional.of(current)
        : Optional.empty();
  }

  @Nullable BigDecimal gatewayMinusReportCash() {
    return SourcesDifference.between(gateway, reportCash)
        .map(SourcesDifference::gatewayMinusReportCash)
        .orElse(null);
  }

  record SourcesDifference(BigDecimal gatewayBalance, BigDecimal reportCash) {

    static Optional<SourcesDifference> between(
        GatewayCash gateway, @Nullable BigDecimal reportCash) {
      return gateway instanceof CurrentDayBalance current && reportCash != null
          ? Optional.of(new SourcesDifference(current.balance(), reportCash))
          : Optional.empty();
    }

    BigDecimal gatewayMinusReportCash() {
      return gatewayBalance.subtract(reportCash);
    }
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("source", source.name());
    if (hold != GatewayHold.NONE) {
      map.put("gatewayNotUsed", hold.name());
    }
    putIfPresent(map, "reportCash", plain(reportCash));
    map.put("gateway", gateway.payload());
    putIfPresent(map, "gatewayMinusReportCash", plain(gatewayMinusReportCash()));
    return map;
  }
}
