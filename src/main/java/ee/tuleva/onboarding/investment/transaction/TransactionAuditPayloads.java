package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.epis.SettlementTimingWarning;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

class TransactionAuditPayloads {

  private TransactionAuditPayloads() {}

  static List<Map<String, Object>> serializeCalculationWarnings(List<CalculationWarning> warnings) {
    return warnings.stream()
        .map(
            warning -> {
              Map<String, Object> map = new LinkedHashMap<>();
              putIfPresent(map, "type", warning.type().name());
              putIfPresent(map, "message", warning.message());
              return map;
            })
        .toList();
  }

  static Map<String, Object> serializeModelDrift(
      FundTransactionInput input, FundCalculationResult result) {
    Map<String, BigDecimal> targetWeights =
        targetWeights(input.modelWeights(), input.grossPortfolioValue(), result.netInvestable());
    Map<String, BigDecimal> weightsAfter = new LinkedHashMap<>();
    result.trades().forEach(trade -> weightsAfter.put(trade.isin(), trade.projectedWeight()));

    List<Map<String, Object>> positions = new ArrayList<>();
    BigDecimal totalBefore = ZERO;
    BigDecimal totalAfter = ZERO;
    for (PositionSnapshot position : input.positions()) {
      BigDecimal target = targetWeights.getOrDefault(position.isin(), ZERO);
      BigDecimal before = currentWeight(position, input.grossPortfolioValue());
      BigDecimal after = weightsAfter.get(position.isin());
      Map<String, Object> map = new LinkedHashMap<>();
      putIfPresent(map, "isin", position.isin());
      putIfPresent(map, "targetWeight", plain(target));
      putIfPresent(map, "weightBefore", plain(before));
      putIfPresent(map, "weightAfter", plain(after));
      if (before != null) {
        BigDecimal driftBefore = before.subtract(target);
        putIfPresent(map, "driftBefore", plain(driftBefore));
        totalBefore = totalBefore.add(driftBefore.abs());
      }
      if (after != null) {
        BigDecimal driftAfter = after.subtract(target);
        putIfPresent(map, "driftAfter", plain(driftAfter));
        totalAfter = totalAfter.add(driftAfter.abs());
      }
      positions.add(map);
    }

    Map<String, Object> drift = new LinkedHashMap<>();
    putIfPresent(drift, "totalAbsoluteDriftBefore", plain(totalBefore));
    putIfPresent(drift, "totalAbsoluteDriftAfter", plain(totalAfter));
    putIfPresent(drift, "positions", positions);
    return drift;
  }

  private static Map<String, BigDecimal> targetWeights(
      List<ModelWeight> modelWeights, BigDecimal grossPortfolioValue, BigDecimal netInvestable) {
    if (grossPortfolioValue.signum() == 0) {
      return Map.of();
    }
    BigDecimal totalWeight =
        modelWeights.stream().map(ModelWeight::weight).reduce(ZERO, BigDecimal::add);
    BigDecimal normalizer = totalWeight.signum() == 0 ? BigDecimal.ONE : totalWeight;
    Map<String, BigDecimal> map = new LinkedHashMap<>();
    modelWeights.forEach(
        weight ->
            map.put(
                weight.isin(),
                weight
                    .weight()
                    .divide(normalizer, 10, RoundingMode.HALF_UP)
                    .multiply(netInvestable)
                    .divide(grossPortfolioValue, 6, RoundingMode.HALF_UP)));
    return map;
  }

  static List<Map<String, Object>> serializeTrades(
      List<TradeCalculation> trades,
      Map<String, TransactionOrder> ordersByIsin,
      Map<String, @Nullable ResolvedPrice> priceResolutions) {
    return trades.stream()
        .map(
            trade ->
                serializeTrade(
                    trade, ordersByIsin.get(trade.isin()), priceResolutions.get(trade.isin())))
        .toList();
  }

  private static Map<String, Object> serializeTrade(
      TradeCalculation trade,
      @Nullable TransactionOrder order,
      @Nullable ResolvedPrice resolvedPrice) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "isin", trade.isin());
    putIfPresent(map, "tradeAmount", plain(trade.tradeAmount()));
    putIfPresent(map, "projectedWeight", plain(trade.projectedWeight()));
    putIfPresent(map, "price", resolvedPrice == null ? null : plain(resolvedPrice.usedPrice()));
    putIfPresent(
        map, "limitStatus", trade.limitStatus() == null ? null : trade.limitStatus().name());
    if (order != null) {
      putIfPresent(map, "quantity", plain(order.getOrderQuantity()));
      putIfPresent(
          map,
          "side",
          order.getTransactionType() == null ? null : order.getTransactionType().name());
      putIfPresent(
          map, "venue", order.getOrderVenue() == null ? null : order.getOrderVenue().name());
      putIfPresent(
          map,
          "instrumentType",
          order.getInstrumentType() == null ? null : order.getInstrumentType().name());
    }
    return map;
  }

  static List<Map<String, Object>> serializeSettlementWarnings(
      List<SettlementTimingWarning> warnings) {
    return warnings.stream().map(TransactionAuditPayloads::serializeSettlementWarning).toList();
  }

  private static Map<String, Object> serializeSettlementWarning(SettlementTimingWarning warning) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "type", warning.type() == null ? null : warning.type().name());
    putIfPresent(map, "fund", warning.fund() == null ? null : warning.fund().name());
    putIfPresent(
        map,
        "sellSettlementDate",
        warning.sellSettlementDate() == null ? null : warning.sellSettlementDate().toString());
    putIfPresent(
        map,
        "deadlineDate",
        warning.deadlineDate() == null ? null : warning.deadlineDate().toString());
    putIfPresent(map, "message", warning.message());
    return map;
  }

  static List<Map<String, Object>> serializePriceResolutions(
      Map<String, @Nullable ResolvedPrice> priceResolutions) {
    return priceResolutions.entrySet().stream()
        .map(entry -> serializePriceResolution(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static Map<String, Object> serializePriceResolution(
      String isin, @Nullable ResolvedPrice resolvedPrice) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "isin", isin);
    if (resolvedPrice != null) {
      putIfPresent(map, "price", plain(resolvedPrice.usedPrice()));
      putIfPresent(
          map,
          "priceDate",
          resolvedPrice.priceDate() == null ? null : resolvedPrice.priceDate().toString());
      putIfPresent(
          map,
          "priceSource",
          resolvedPrice.priceSource() == null ? null : resolvedPrice.priceSource().name());
      putIfPresent(
          map,
          "validationStatus",
          resolvedPrice.validationStatus() == null
              ? null
              : resolvedPrice.validationStatus().name());
    }
    return map;
  }

  @Nullable
  private static BigDecimal currentWeight(
      PositionSnapshot position, BigDecimal grossPortfolioValue) {
    if (grossPortfolioValue.signum() == 0 || position.marketValue() == null) {
      return null;
    }
    return position.marketValue().divide(grossPortfolioValue, 6, RoundingMode.HALF_UP);
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
}
