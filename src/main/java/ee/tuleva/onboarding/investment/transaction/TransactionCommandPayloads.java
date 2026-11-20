package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.plain;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
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
    AcceptedHoldingDifferences accepted = command.getAcceptedHoldingDifferences();
    if (accepted != null) {
      payload.put(
          "acceptedHoldingDifferences",
          Map.of("isins", accepted.sortedIsins(), "comment", accepted.comment()));
    }
    if (command.getPriceDateRule() != PriceDateRule.FRESHEST_PER_INSTRUMENT) {
      payload.put("priceDateRule", command.getPriceDateRule().name());
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
    AcceptedHoldingDifferences accepted = command.getAcceptedHoldingDifferences();
    if (accepted != null) {
      payload.put(
          "acceptedHoldingDifferences",
          acceptedDifferences(accepted.comment(), gates.acceptedDifferences()));
    }
    if (gates.pricing() instanceof TradePrepPricing.OnCommonDate onCommonDate) {
      payload.put("commonPriceDate", onCommonDate.date().toString());
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

  private static Map<String, Object> acceptedDifferences(
      String comment, List<AcceptedHoldingDifference> differences) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("comment", comment);
    map.put(
        "instruments",
        differences.stream().map(TransactionCommandPayloads::acceptedDifference).toList());
    return map;
  }

  private static Map<String, Object> acceptedDifference(AcceptedHoldingDifference difference) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("isin", difference.isin());
    map.put("neededByGate", difference.need().neededByGate());
    putIfPresent(map, "registryQuantity", plain(difference.registryQuantity()));
    putIfPresent(map, "reportQuantity", plain(difference.reportQuantity()));
    return map;
  }
}
