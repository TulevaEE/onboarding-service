package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.Map;

record PendingOrderImpact(
    BigDecimal pendingBuys,
    BigDecimal pendingSells,
    Map<String, BigDecimal> unreportedBuyValues,
    Map<String, BigDecimal> unreportedBuyQuantities,
    BigDecimal buysInTheBalance,
    BigDecimal sellsInTheBalance,
    Map<String, BigDecimal> unreportedSellQuantities,
    Map<String, BigDecimal> unreportedSellValues,
    Map<String, BigDecimal> unreportedBuyValuesWithoutUnits) {

  static PendingOrderImpact none() {
    return new PendingOrderImpact(
        ZERO, ZERO, Map.of(), Map.of(), ZERO, ZERO, Map.of(), Map.of(), Map.of());
  }
}
