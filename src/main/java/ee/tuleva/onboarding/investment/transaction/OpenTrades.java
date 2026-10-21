package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

public record OpenTrades(
    List<OpenTrade> trades,
    Set<TulevaFund> fundsWithIncompleteRows,
    List<String> unattributedClientNames) {

  private static final List<BiPredicate<OpenTrade, OpenTrade>> PAIRING_PASSES =
      List.of(
          OpenTrade::hasSameReferenceAs,
          (earlier, later) ->
              earlier.reference() != null
                  && later.reference() == null
                  && earlier.isSameTradeAs(later),
          (earlier, later) ->
              earlier.reference() == null
                  && later.reference() != null
                  && earlier.isSameTradeAs(later),
          (earlier, later) ->
              earlier.reference() == null
                  && later.reference() == null
                  && earlier.isSameTradeAs(later));

  private static final Comparator<OpenTrade> PAIRING_ORDER =
      Comparator.comparing(OpenTrade::reference, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::side)
          .thenComparing(OpenTrade::isin, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::quantity, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::tradeDate, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::total);

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
    var earlierTrades = of(fund);
    var laterTrades = later.of(fund);
    var unpairedEarlier = inPairingOrder(earlierTrades);
    var unpairedLater = inPairingOrder(laterTrades);
    PAIRING_PASSES.forEach(sameTrade -> removePairs(unpairedEarlier, unpairedLater, sameTrade));
    return new Difference(
        inListOrder(earlierTrades, unpairedEarlier), inListOrder(laterTrades, unpairedLater));
  }

  private static List<OpenTrade> inPairingOrder(List<OpenTrade> trades) {
    var sorted = new ArrayList<>(trades);
    sorted.sort(PAIRING_ORDER);
    return sorted;
  }

  private static void removePairs(
      List<OpenTrade> earlier, List<OpenTrade> later, BiPredicate<OpenTrade, OpenTrade> sameTrade) {
    for (var trades = earlier.iterator(); trades.hasNext(); ) {
      var trade = trades.next();
      for (var candidates = later.iterator(); candidates.hasNext(); ) {
        if (sameTrade.test(trade, candidates.next())) {
          candidates.remove();
          trades.remove();
          break;
        }
      }
    }
  }

  private static List<OpenTrade> inListOrder(List<OpenTrade> trades, List<OpenTrade> unpaired) {
    var remaining = new IdentityHashMap<OpenTrade, Integer>();
    unpaired.forEach(trade -> remaining.merge(trade, 1, Integer::sum));
    var inOrder = new ArrayList<OpenTrade>();
    for (var trade : trades) {
      if (remaining.getOrDefault(trade, 0) > 0) {
        remaining.merge(trade, -1, Integer::sum);
        inOrder.add(trade);
      }
    }
    return List.copyOf(inOrder);
  }

  public record Difference(List<OpenTrade> onlyEarlier, List<OpenTrade> onlyLater) {}
}
