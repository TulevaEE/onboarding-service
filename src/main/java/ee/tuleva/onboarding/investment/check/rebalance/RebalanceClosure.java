package ee.tuleva.onboarding.investment.check.rebalance;

import static java.util.Comparator.comparing;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

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

  static Optional<RebalanceClosure> whicheverFirst(
      Optional<LocalDate> firstInLineNavDate, Optional<LocalDate> firstRebalancingTradeDate) {
    return Stream.concat(
            firstInLineNavDate.map(RebalanceClosure::byAnInLineNav).stream(),
            firstRebalancingTradeDate.map(RebalanceClosure::byRebalancingTrades).stream())
        .min(comparing(RebalanceClosure::on));
  }
}
