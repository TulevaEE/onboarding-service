package ee.tuleva.onboarding.investment.check.health;

import ee.tuleva.onboarding.investment.transaction.PendingTrade;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

record PendingTradeChanges(
    @Nullable String unavailableReason,
    List<PendingTrade> appeared,
    List<PendingTrade> disappeared) {

  static PendingTradeChanges between(
      TulevaFund fund, PendingTradeReport previous, PendingTradeReport current) {
    var reason = previous.unavailableReason(fund).or(() -> current.unavailableReason(fund));
    if (reason.isPresent()) {
      return unavailable(reason.get());
    }
    var before = previous.tradesOf(fund);
    var after = current.tradesOf(fund);
    return new PendingTradeChanges(null, unmatched(after, before), unmatched(before, after));
  }

  static PendingTradeChanges unavailable(String reason) {
    return new PendingTradeChanges(reason, List.of(), List.of());
  }

  BigDecimal disappearedTotal(TransactionType side) {
    return PendingTradeReport.totalOf(disappeared, side);
  }

  private static List<PendingTrade> unmatched(
      List<PendingTrade> trades, List<PendingTrade> counterparts) {
    var remaining = new ArrayList<>(counterparts);
    var unmatched = new ArrayList<PendingTrade>();
    for (var trade : trades) {
      var match = remaining.stream().filter(trade::isSameTradeAs).findFirst();
      match.ifPresentOrElse(remaining::remove, () -> unmatched.add(trade));
    }
    return List.copyOf(unmatched);
  }
}
