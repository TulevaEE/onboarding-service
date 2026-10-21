package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import java.util.Map;

public record PendingTrades(
    List<PendingTrade> trades,
    Map<TulevaFund, Integer> unusableRowsByFund,
    int unattributableRows) {

  public List<PendingTrade> of(TulevaFund fund) {
    return trades.stream().filter(trade -> trade.fund() == fund).toList();
  }
}
