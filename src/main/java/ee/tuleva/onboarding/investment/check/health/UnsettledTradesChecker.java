package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNSETTLED_TRADES;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class UnsettledTradesChecker {

  private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.01");

  List<HealthCheckFinding> check(
      TulevaFund fund,
      LocalDate navDate,
      List<FundPosition> liabilities,
      List<FundPosition> receivables,
      @Nullable OpenTrades openTrades,
      @Nullable OpenTrades previousOpenTrades) {
    var tradeRows =
        Stream.concat(
                liabilities.stream().filter(FundPosition::isTradePayable),
                receivables.stream().filter(FundPosition::isTradeReceivable))
            .toList();
    if (tradeRows.stream().anyMatch(row -> row.getMarketValue() == null)) {
      return List.of(notRun(fund, "a trade payables or receivables row has no amount"));
    }
    var tradePayables = sum(liabilities, FundPosition::isTradePayable).negate();
    var tradeReceivables = sum(receivables, FundPosition::isTradeReceivable);
    if (tradePayables.signum() == 0 && tradeReceivables.signum() == 0) {
      return List.of();
    }
    if (openTrades == null) {
      return List.of(
          notRun(
              fund,
              "no SEB trade list for %s; trade payables %s, receivables %s"
                  .formatted(navDate, amount(tradePayables), amount(tradeReceivables))));
    }
    if (openTrades.fundsWithIncompleteRows().contains(fund)) {
      return List.of(
          notRun(fund, "SEB's trade list has a row of this fund without a side or amount"));
    }
    return Stream.of(
            excess(fund, BUY, tradePayables, openTrades, previousOpenTrades, PURCHASES),
            excess(fund, SELL, tradeReceivables, openTrades, previousOpenTrades, SALES))
        .filter(Objects::nonNull)
        .toList();
  }

  private @Nullable HealthCheckFinding excess(
      TulevaFund fund,
      TransactionType side,
      BigDecimal booked,
      OpenTrades openTrades,
      @Nullable OpenTrades previousOpenTrades,
      Wording wording) {
    var open = openTrades.total(fund, side);
    var excess = booked.subtract(open);
    if (excess.compareTo(ROUNDING_TOLERANCE) <= 0) {
      return null;
    }
    if (!openTrades.unattributedClientNames().isEmpty()) {
      return notRun(
          fund,
          "excess %s, and SEB's trade list has rows of no known fund: %s"
              .formatted(amount(excess), openTrades.unattributedClientNames()));
    }
    return new HealthCheckFinding(
        fund,
        UNSETTLED_TRADES,
        FAIL,
        "%s %s, %s %s, excess %s, NAV %s. %s"
            .formatted(
                wording.bookedName(),
                amount(booked),
                wording.openName(),
                amount(open),
                amount(excess),
                wording.navEffect(),
                cause(fund, side, excess, openTrades, previousOpenTrades)));
  }

  private static String cause(
      TulevaFund fund,
      TransactionType side,
      BigDecimal excess,
      OpenTrades openTrades,
      @Nullable OpenTrades previousOpenTrades) {
    if (previousOpenTrades == null) {
      return "Previous SEB trade list missing";
    }
    var left =
        previousOpenTrades.missingFrom(openTrades, fund).stream()
            .filter(trade -> trade.side() == side)
            .toList();
    var equalToExcess =
        left.stream().filter(trade -> isWithinRounding(trade.total(), excess)).toList();
    if (equalToExcess.size() == 1) {
      return "Cause: %s left SEB's trade list%s"
          .formatted(identify(equalToExcess.getFirst()), settlement(equalToExcess.getFirst()));
    }
    if (left.isEmpty()) {
      return "No trade left SEB's trade list";
    }
    return "Left SEB's trade list: "
        + left.stream().map(trade -> identify(trade) + settlement(trade)).collect(joining("; "));
  }

  private static String identify(OpenTrade trade) {
    return Stream.of(trade.reference(), trade.isin(), amount(trade.total()))
        .filter(Objects::nonNull)
        .collect(joining(" "));
  }

  private static String settlement(OpenTrade trade) {
    return trade.settlementDate() == null ? "" : ", settlement " + trade.settlementDate();
  }

  private static boolean isWithinRounding(BigDecimal amount, BigDecimal excess) {
    return amount.subtract(excess).abs().compareTo(ROUNDING_TOLERANCE) <= 0;
  }

  private static HealthCheckFinding notRun(TulevaFund fund, String reason) {
    return new HealthCheckFinding(fund, UNSETTLED_TRADES, NOT_RUN, "Not checked: " + reason);
  }

  private static BigDecimal sum(List<FundPosition> positions, Predicate<FundPosition> include) {
    return positions.stream()
        .filter(include)
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private static String amount(BigDecimal value) {
    return value.setScale(2, HALF_UP).toPlainString();
  }

  private static final Wording PURCHASES = new Wording("Trade payables", "open buys", "too low");
  private static final Wording SALES = new Wording("Trade receivables", "open sells", "too high");

  private record Wording(String bookedName, String openName, String navEffect) {}
}
