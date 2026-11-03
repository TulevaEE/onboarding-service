package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.BigDecimal.ZERO;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

record TradedQuantity(BigDecimal bought, BigDecimal sold) {

  static final TradedQuantity NONE = new TradedQuantity(ZERO, ZERO);

  static Map<String, TradedQuantity> byIsin(List<OpenTrade> trades) {
    return trades.stream()
        .collect(
            groupingBy(
                trade -> requireNonNull(trade.isin(), "Open trade without an ISIN"),
                reducing(NONE, TradedQuantity::of, TradedQuantity::plus)));
  }

  private static TradedQuantity of(OpenTrade trade) {
    var quantity = requireNonNull(trade.quantity(), "Open trade without a quantity");
    return trade.side() == BUY
        ? new TradedQuantity(quantity, ZERO)
        : new TradedQuantity(ZERO, quantity);
  }

  BigDecimal netChange() {
    return bought.subtract(sold);
  }

  private TradedQuantity plus(TradedQuantity other) {
    return new TradedQuantity(bought.add(other.bought), sold.add(other.sold));
  }
}
