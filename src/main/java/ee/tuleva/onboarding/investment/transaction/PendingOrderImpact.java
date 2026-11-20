package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.Map;

record PendingOrderImpact(
    BigDecimal pendingBuys,
    BigDecimal pendingSells,
    Map<String, BigDecimal> unreportedPositionValues,
    Map<String, BigDecimal> unreportedPositionQuantities,
    BigDecimal buysInTheBalance,
    BigDecimal sellsInTheBalance) {

  static PendingOrderImpact none() {
    return new PendingOrderImpact(ZERO, ZERO, Map.of(), Map.of(), ZERO, ZERO);
  }
}
