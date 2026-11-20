package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class TransactionCommandPayloads {

  private TransactionCommandPayloads() {}

  static Map<String, Object> failed(
      TransactionCommand command, @Nullable FundTransactionInput input, RuntimeException e) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fund", command.getFund().name());
    payload.put("asOfDate", command.getAsOfDate().toString());
    payload.put("manualAdjustments", Map.copyOf(command.getManualAdjustments()));
    putIfPresent(payload, "cash", plain(command.getCash()));
    putIfPresent(payload, "cashComment", command.getCashComment());
    putExcludeIsins(payload, command);
    if (!command.getPrices().isEmpty()) {
      payload.put("prices", requestedPrices(command.getPrices()));
    }
    if (input != null) {
      payload.put(
          "input", TransactionInputPayloads.serializeInput(input, command.getManualAdjustments()));
      if (!input.inputWarnings().isEmpty()) {
        payload.put(
            "calculationWarnings",
            TransactionAuditPayloads.serializeCalculationWarnings(input.inputWarnings()));
      }
    }
    payload.put("exceptionClass", e.getClass().getName());
    putIfPresent(payload, "errorMessage", command.getErrorMessage());
    return payload;
  }

  static Map<String, Object> completed(
      TransactionCommand command,
      GateCheck gates,
      FundTransactionInput input,
      FundCalculationResult result,
      CalculatedOrders calculated,
      List<Map<String, Object>> settlementWarnings) {
    List<TransactionOrder> orders = calculated.orders();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(
        "input", TransactionInputPayloads.serializeInput(input, command.getManualAdjustments()));
    putIfPresent(payload, "cashComment", command.getCashComment());
    putExcludeIsins(payload, command);
    if (!gates.priceOverrides().isEmpty()) {
      payload.put("priceOverrides", priceOverrides(gates.priceOverrides()));
    }
    payload.put(
        "output",
        TransactionAuditPayloads.serializeTrades(
            result.trades(), ordersByIsin(orders), calculated.priceResolutions()));
    payload.put(
        "priceResolutions",
        TransactionAuditPayloads.serializePriceResolutions(calculated.priceResolutions()));
    payload.put("settlementWarnings", settlementWarnings);
    payload.put(
        "calculationWarnings",
        TransactionAuditPayloads.serializeCalculationWarnings(result.warnings()));
    payload.put("modelDrift", TransactionAuditPayloads.serializeModelDrift(input, result));
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("fund", command.getFund().name());
    summary.put("mode", command.getMode().name());
    summary.put("tradeCount", orders.size());
    putIfPresent(summary, "netInvestable", plain(result.netInvestable()));
    putIfPresent(summary, "noTradeReason", result.noTradeReason());
    payload.put("summary", summary);
    return payload;
  }

  private static Map<String, TransactionOrder> ordersByIsin(List<TransactionOrder> orders) {
    Map<String, TransactionOrder> map = new LinkedHashMap<>();
    orders.forEach(order -> map.put(order.getInstrumentIsin(), order));
    return map;
  }

  private static void putExcludeIsins(Map<String, Object> payload, TransactionCommand command) {
    if (!command.getExcludeIsins().isEmpty()) {
      payload.put("excludeIsins", command.sortedExcludeIsins());
    }
  }

  private static void putIfPresent(Map<String, Object> map, String key, @Nullable Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  @Nullable
  private static String plain(@Nullable BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }

  private static List<Map<String, Object>> priceOverrides(List<UsedPriceOverride> overrides) {
    return overrides.stream().map(TransactionCommandPayloads::priceOverride).toList();
  }

  private static List<Map<String, Object>> requestedPrices(List<PriceOverride> prices) {
    return prices.stream().map(TransactionCommandPayloads::requestedPrice).toList();
  }

  private static Map<String, Object> priceOverride(UsedPriceOverride used) {
    Map<String, Object> map = requestedPrice(used.override());
    map.put("neededByGate", used.need().neededByGate());
    ResolvedPrice indexPrice = used.indexPrice();
    if (indexPrice != null) {
      putIfPresent(map, "indexPrice", plain(indexPrice.usedPrice()));
      putIfPresent(
          map,
          "indexPriceDate",
          indexPrice.priceDate() == null ? null : indexPrice.priceDate().toString());
    }
    return map;
  }

  private static Map<String, Object> requestedPrice(PriceOverride price) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("isin", price.isin());
    map.put("price", price.price().toPlainString());
    map.put("comment", price.comment());
    return map;
  }
}
