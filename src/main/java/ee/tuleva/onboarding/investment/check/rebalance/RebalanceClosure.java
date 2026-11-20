package ee.tuleva.onboarding.investment.check.rebalance;

import java.time.LocalDate;

public record RebalanceClosure(LocalDate on, ClosedBy by) {

  public enum ClosedBy {
    AN_IN_LINE_NAV,
    REBALANCING_TRADES
  }

  static RebalanceClosure byAnInLineNav(LocalDate navDate) {
    return new RebalanceClosure(navDate, ClosedBy.AN_IN_LINE_NAV);
  }

  static RebalanceClosure byRebalancingTrades(LocalDate tradeDate) {
    return new RebalanceClosure(tradeDate, ClosedBy.REBALANCING_TRADES);
  }
}
