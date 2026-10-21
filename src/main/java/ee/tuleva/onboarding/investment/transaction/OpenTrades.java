package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

  public List<OpenTrade> missingFrom(OpenTrades later, TulevaFund fund) {
    var unmatchedLater = new ArrayList<>(later.of(fund));
    var missing = new ArrayList<OpenTrade>();
    for (var trade : of(fund)) {
      unmatchedLater.stream()
          .filter(trade::isSameTradeAs)
          .findFirst()
          .ifPresentOrElse(unmatchedLater::remove, () -> missing.add(trade));
    }
    return List.copyOf(missing);
  }
}
