package ee.tuleva.onboarding.investment.check.health;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

record OpenTradeChanges(
    @Nullable String unavailableReason,
    List<OpenTrade> appeared,
    List<OpenTrade> disappeared,
    List<String> unknownFundRows) {

  static OpenTradeChanges between(
      TulevaFund fund,
      LocalDate previousNavDate,
      @Nullable OpenTrades previous,
      LocalDate navDate,
      @Nullable OpenTrades current) {
    if (previous == null) {
      return noTradeList(previousNavDate);
    }
    if (hasIncompleteRow(previous, fund)) {
      return incompleteRow(previousNavDate);
    }
    if (current == null) {
      return noTradeList(navDate);
    }
    if (hasIncompleteRow(current, fund)) {
      return incompleteRow(navDate);
    }
    var difference = previous.comparedWith(current, fund);
    return new OpenTradeChanges(
        null,
        difference.onlyLater(),
        difference.onlyEarlier(),
        Stream.concat(
                previous.unattributedClientNames().stream(),
                current.unattributedClientNames().stream())
            .distinct()
            .toList());
  }

  static OpenTradeChanges unavailable(String reason) {
    return new OpenTradeChanges(reason, List.of(), List.of(), List.of());
  }

  BigDecimal disappearedTotal(TransactionType side) {
    return disappeared.stream()
        .filter(trade -> trade.side() == side)
        .map(OpenTrade::total)
        .reduce(ZERO, BigDecimal::add);
  }

  private static OpenTradeChanges noTradeList(LocalDate navDate) {
    return unavailable("no SEB trade list for %s".formatted(navDate));
  }

  private static OpenTradeChanges incompleteRow(LocalDate navDate) {
    return unavailable(
        "SEB trade list for %s has an incomplete row of this fund".formatted(navDate));
  }

  private static boolean hasIncompleteRow(OpenTrades tradeList, TulevaFund fund) {
    return tradeList.fundsWithIncompleteRows().contains(fund)
        || tradeList.of(fund).stream().anyMatch(OpenTradeChanges::lacksIsinOrQuantity);
  }

  private static boolean lacksIsinOrQuantity(OpenTrade trade) {
    return trade.isin() == null || trade.quantity() == null;
  }
}
