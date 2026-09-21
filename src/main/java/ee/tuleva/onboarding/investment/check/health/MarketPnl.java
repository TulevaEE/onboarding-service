package ee.tuleva.onboarding.investment.check.health;

import java.math.BigDecimal;
import java.util.List;

record MarketPnl(
    BigDecimal amount,
    List<String> unpricedHoldings,
    List<String> unexplainedExits,
    List<ExitLeg> exitLegs) {

  boolean isComplete() {
    return unpricedHoldings.isEmpty() && unexplainedExits.isEmpty();
  }
}
