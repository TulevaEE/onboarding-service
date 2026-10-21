package ee.tuleva.onboarding.investment.check.health;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record OpenTradeChanges(
    @Nullable String unavailableReason, List<OpenTrade> appeared, List<OpenTrade> disappeared) {

  static OpenTradeChanges between(
      TulevaFund fund,
      LocalDate previousNavDate,
      @Nullable OpenTrades previous,
      LocalDate navDate,
      @Nullable OpenTrades current) {
    var reason =
        unusable(fund, previousNavDate, previous).or(() -> unusable(fund, navDate, current));
    if (reason.isPresent() || previous == null || current == null) {
      return unavailable(reason.orElseThrow());
    }
    var difference = previous.comparedWith(current, fund);
    return new OpenTradeChanges(null, difference.onlyLater(), difference.onlyEarlier());
  }

  static OpenTradeChanges unavailable(String reason) {
    return new OpenTradeChanges(reason, List.of(), List.of());
  }

  BigDecimal disappearedTotal(TransactionType side) {
    return disappeared.stream()
        .filter(trade -> trade.side() == side)
        .map(OpenTrade::total)
        .reduce(ZERO, BigDecimal::add);
  }

  private static Optional<String> unusable(
      TulevaFund fund, LocalDate navDate, @Nullable OpenTrades tradeList) {
    if (tradeList == null) {
      return Optional.of("no SEB trade list for %s".formatted(navDate));
    }
    if (tradeList.fundsWithIncompleteRows().contains(fund)
        || tradeList.of(fund).stream().anyMatch(OpenTradeChanges::lacksIsinOrQuantity)) {
      return Optional.of(
          "SEB trade list for %s has an incomplete row of this fund".formatted(navDate));
    }
    if (!tradeList.unattributedClientNames().isEmpty()) {
      return Optional.of(
          "SEB trade list for %s has rows of no known fund: %s"
              .formatted(navDate, tradeList.unattributedClientNames()));
    }
    return Optional.empty();
  }

  private static boolean lacksIsinOrQuantity(OpenTrade trade) {
    return trade.isin() == null || trade.quantity() == null;
  }
}
