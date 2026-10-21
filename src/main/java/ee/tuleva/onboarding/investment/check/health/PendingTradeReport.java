package ee.tuleva.onboarding.investment.check.health;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.PendingTrade;
import ee.tuleva.onboarding.investment.transaction.PendingTrades;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record PendingTradeReport(LocalDate asOf, @Nullable PendingTrades trades) {

  Optional<String> unavailableReason(TulevaFund fund) {
    if (trades == null) {
      return Optional.of("no SEB pending transactions report as of %s".formatted(asOf));
    }
    int unusableRows =
        trades.unattributableRows() + trades.unusableRowsByFund().getOrDefault(fund, 0);
    if (unusableRows > 0) {
      return Optional.of(
          "SEB pending transactions report as of %s has %d unusable rows"
              .formatted(asOf, unusableRows));
    }
    return Optional.empty();
  }

  List<PendingTrade> tradesOf(TulevaFund fund) {
    return trades == null ? List.of() : trades.of(fund);
  }

  BigDecimal totalOf(TulevaFund fund, TransactionType side) {
    return totalOf(tradesOf(fund), side);
  }

  static BigDecimal totalOf(List<PendingTrade> trades, TransactionType side) {
    return trades.stream()
        .filter(trade -> trade.side() == side)
        .map(PendingTrade::total)
        .reduce(ZERO, BigDecimal::add);
  }
}
