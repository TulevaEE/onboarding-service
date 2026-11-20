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
    List<CalculationWarning> warnings) {

  CashSourceReport withWarnings(List<CalculationWarning> differenceWarnings) {
    return new CashSourceReport(source, gateway, reportCash, differenceWarnings);
  }

  Optional<CurrentDayBalance> gatewayBalanceInUse() {
    return source == CashSource.GATEWAY_BALANCE && gateway instanceof CurrentDayBalance current
        ? Optional.of(current)
        : Optional.empty();
  }

  Optional<SourcesDifference> sourcesDifference() {
    return gateway instanceof CurrentDayBalance current && reportCash != null
        ? Optional.of(new SourcesDifference(current.balance(), reportCash))
        : Optional.empty();
  }

  @Nullable BigDecimal gatewayMinusReportCash() {
    return sourcesDifference().map(SourcesDifference::gatewayMinusReportCash).orElse(null);
  }

  record SourcesDifference(BigDecimal gatewayBalance, BigDecimal reportCash) {

    BigDecimal gatewayMinusReportCash() {
      return gatewayBalance.subtract(reportCash);
    }
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
