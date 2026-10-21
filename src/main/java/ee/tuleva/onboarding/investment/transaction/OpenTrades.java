package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

public record OpenTrades(
    List<OpenTrade> trades,
    Set<TulevaFund> fundsWithIncompleteRows,
    List<String> unattributedClientNames) {

  public OpenTrades {
    trades = List.copyOf(trades);
    fundsWithIncompleteRows = Set.copyOf(fundsWithIncompleteRows);
    unattributedClientNames = List.copyOf(unattributedClientNames);
  }

  public List<OpenTrade> of(TulevaFund fund) {
    return trades.stream().filter(trade -> trade.fund() == fund).toList();
  }

  public BigDecimal total(TulevaFund fund, TransactionType side) {
    return of(fund).stream()
        .filter(trade -> trade.side() == side)
        .map(OpenTrade::total)
        .reduce(ZERO, BigDecimal::add);
  }

  public Difference comparedWith(OpenTrades later, TulevaFund fund) {
    var onlyEarlier = new ArrayList<>(of(fund));
    var onlyLater = new ArrayList<>(later.of(fund));
    removePairs(onlyEarlier, onlyLater, OpenTrade::hasSameReferenceAs);
    removePairs(onlyEarlier, onlyLater, OpenTrade::isSameTradeAs);
    return new Difference(List.copyOf(onlyEarlier), List.copyOf(onlyLater));
  }

  private static void removePairs(
      List<OpenTrade> earlier, List<OpenTrade> later, BiPredicate<OpenTrade, OpenTrade> sameTrade) {
    for (var trades = earlier.iterator(); trades.hasNext(); ) {
      var trade = trades.next();
      later.stream()
          .filter(candidate -> sameTrade.test(trade, candidate))
          .findFirst()
          .ifPresent(
              match -> {
                later.remove(match);
                trades.remove();
              });
    }
  }

  public record Difference(List<OpenTrade> onlyEarlier, List<OpenTrade> onlyLater) {}
}
