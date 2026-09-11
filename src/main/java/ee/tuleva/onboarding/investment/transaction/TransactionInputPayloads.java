package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class TransactionInputPayloads {

  private TransactionInputPayloads() {}

  static Map<String, Object> serializeInput(
      FundTransactionInput input, Map<String, Object> manualAdjustments) {
    Map<String, Object> result = new LinkedHashMap<>();
    putIfPresent(
        result,
        "positions",
        input.positions().stream()
            .map(position -> serializePosition(position, input.grossPortfolioValue()))
            .toList());
    putIfPresent(
        result,
        "modelWeights",
        input.modelWeights().stream().map(TransactionInputPayloads::serializeModelWeight).toList());
    putIfPresent(result, "grossPortfolioValue", plain(input.grossPortfolioValue()));
    putIfPresent(result, "cashBuffer", plain(input.cashBuffer()));
    putIfPresent(result, "liabilities", plain(input.liabilities()));
    putIfPresent(result, "receivables", plain(input.receivables()));
    putIfPresent(result, "freeCash", plain(input.freeCash()));
    putIfPresent(result, "minTransactionThreshold", plain(input.minTransactionThreshold()));
    putIfPresent(
        result,
        "positionDate",
        input.positionDate() == null ? null : input.positionDate().toString());
    putIfPresent(
        result,
        "modelEffectiveDate",
        input.modelEffectiveDate() == null ? null : input.modelEffectiveDate().toString());
    putIfPresent(
        result, "liabilityBreakdown", serializeLiabilityBreakdown(input.liabilityBreakdown()));
    putIfPresent(result, "reportCash", plain(input.reportCash()));
    putIfPresent(result, "appliedCash", plain(input.appliedCash()));
    putIfPresent(result, "ledgerCash", plain(input.ledgerCash()));
    putIfPresent(result, "cashDifference", plain(cashDifference(input)));
    putIfPresent(result, "positionLimits", serializePositionLimits(input.positionLimits()));
    putIfPresent(result, "fastSellIsins", List.copyOf(input.fastSellIsins()));
    putIfPresent(result, "manualAdjustments", Map.copyOf(manualAdjustments));
    return result;
  }

  @Nullable
  private static Map<String, Object> serializeLiabilityBreakdown(
      @Nullable LiabilityBreakdown breakdown) {
    if (breakdown == null) {
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "managementFee", plain(breakdown.managementFee()));
    putIfPresent(map, "depotFee", plain(breakdown.depotFee()));
    putIfPresent(map, "pevaRava", plain(breakdown.pevaRava()));
    putIfPresent(map, "r16", plain(breakdown.r16()));
    putIfPresent(map, "r45Net", plain(breakdown.r45Net()));
    putIfPresent(map, "pendingBuys", plain(breakdown.pendingBuys()));
    putIfPresent(map, "pendingSells", plain(breakdown.pendingSells()));
    putIfPresent(map, "unreconciledBankReceipts", plain(breakdown.unreconciledBankReceipts()));
    putIfPresent(map, "fundUnitsReservedValue", plain(breakdown.fundUnitsReservedValue()));
    putIfPresent(map, "incomingPaymentsClearing", plain(breakdown.incomingPaymentsClearing()));
    return map;
  }

  @Nullable
  private static BigDecimal cashDifference(FundTransactionInput input) {
    if (input.reportCash() == null || input.ledgerCash() == null) {
      return null;
    }
    return input.reportCash().subtract(input.ledgerCash());
  }

  private static Map<String, Object> serializePosition(
      PositionSnapshot position, BigDecimal grossPortfolioValue) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "isin", position.isin());
    putIfPresent(map, "marketValue", plain(position.marketValue()));
    putIfPresent(map, "quantity", plain(position.quantity()));
    putIfPresent(map, "unitPrice", plain(position.unitPrice()));
    putIfPresent(map, "currentWeight", plain(currentWeight(position, grossPortfolioValue)));
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

  private static Map<String, Object> serializeModelWeight(ModelWeight weight) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "isin", weight.isin());
    putIfPresent(map, "weight", plain(weight.weight()));
    return map;
  }

  private static Map<String, Map<String, Object>> serializePositionLimits(
      Map<String, PositionLimitSnapshot> positionLimits) {
    Map<String, Map<String, Object>> map = new LinkedHashMap<>();
    positionLimits.forEach(
        (isin, limit) -> {
          Map<String, Object> limitMap = new LinkedHashMap<>();
          putIfPresent(limitMap, "softLimit", plain(limit.softLimit()));
          putIfPresent(limitMap, "hardLimit", plain(limit.hardLimit()));
          map.put(isin, limitMap);
        });
    return map;
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
