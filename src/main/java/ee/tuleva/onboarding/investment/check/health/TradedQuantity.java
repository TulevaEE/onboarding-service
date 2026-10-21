package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

import ee.tuleva.onboarding.investment.transaction.PendingTrade;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

record TradedQuantity(BigDecimal bought, BigDecimal sold) {

  static final TradedQuantity NONE = new TradedQuantity(ZERO, ZERO);

  static Map<String, TradedQuantity> byIsin(List<PendingTrade> trades) {
    return trades.stream()
        .collect(
            groupingBy(
                PendingTrade::isin, reducing(NONE, TradedQuantity::of, TradedQuantity::plus)));
  }

  private static TradedQuantity of(PendingTrade trade) {
    return trade.side() == BUY
        ? new TradedQuantity(trade.quantity(), ZERO)
        : new TradedQuantity(ZERO, trade.quantity());
  }

  BigDecimal netChange() {
    return bought.subtract(sold);
  }

  private TradedQuantity plus(TradedQuantity other) {
    return new TradedQuantity(bought.add(other.bought), sold.add(other.sold));
  }
}
