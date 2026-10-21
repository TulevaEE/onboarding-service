package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNSETTLED_TRADES;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

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
      return List.of(tradeRowWithoutAmount(fund, navDate));
    }
    var tradePayables = sum(liabilities, FundPosition::isTradePayable).negate();
    var tradeReceivables = sum(receivables, FundPosition::isTradeReceivable);
    if (tradePayables.signum() == 0 && tradeReceivables.signum() == 0) {
      return List.of();
    }
    if (openTrades == null) {
      return List.of(tradeListNotImported(fund, navDate, tradePayables, tradeReceivables));
    }
    if (openTrades.fundsWithIncompleteRows().contains(fund)) {
      return List.of(fundsTradeRowIncomplete(fund, navDate));
    }
    return Stream.of(
            excess(fund, navDate, BUY, tradePayables, openTrades, previousOpenTrades, PURCHASES),
            excess(fund, navDate, SELL, tradeReceivables, openTrades, previousOpenTrades, SALES))
        .filter(Objects::nonNull)
        .toList();
  }

  private @Nullable HealthCheckFinding excess(
      TulevaFund fund,
      LocalDate navDate,
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
      return excessUnrecognisedTradesCouldExplain(fund, navDate, excess, openTrades);
    }
    return new HealthCheckFinding(
        fund,
        UNSETTLED_TRADES,
        FAIL,
        ("SEB counts more %s than its trade list has open, so NAV would be %s: fund=%s,"
                + " navDate=%s, %s=%s, %s=%s, excess=%s, leftTradeList=[%s]")
            .formatted(
                wording.description(),
                wording.navEffect(),
                fund.getCode(),
                navDate,
                wording.bookedName(),
                amount(booked),
                wording.openName(),
                amount(open),
                amount(excess),
                leftTradeList(fund, side, excess, openTrades, previousOpenTrades)));
  }

  private String leftTradeList(
      TulevaFund fund,
      TransactionType side,
      BigDecimal excess,
      OpenTrades openTrades,
      @Nullable OpenTrades previousOpenTrades) {
    if (previousOpenTrades == null) {
      return "previous trade list not imported";
    }
    var stillOpen =
        openTrades.of(fund).stream()
            .map(OpenTrade::reference)
            .filter(Objects::nonNull)
            .collect(toSet());
    return previousOpenTrades.of(fund).stream()
        .filter(trade -> trade.side() == side)
        .filter(trade -> !stillOpen.contains(trade.reference()))
        .map(trade -> describe(trade, excess))
        .collect(joining(", "));
  }

  private static String describe(OpenTrade trade, BigDecimal excess) {
    var description =
        "%s %s %s settling %s"
            .formatted(
                trade.reference(), trade.isin(), amount(trade.total()), trade.settlementDate());
    var equalsExcess = trade.total().subtract(excess).abs().compareTo(ROUNDING_TOLERANCE) <= 0;
    return equalsExcess ? description + " (equals the excess)" : description;
  }

  private HealthCheckFinding fundsTradeRowIncomplete(TulevaFund fund, LocalDate navDate) {
    return new HealthCheckFinding(
        fund,
        UNSETTLED_TRADES,
        NOT_RUN,
        ("SEB's trade list has a row for this fund without a side or an amount, so trade payables"
                + " and receivables cannot be checked against open trades: fund=%s, navDate=%s")
            .formatted(fund.getCode(), navDate));
  }

  private HealthCheckFinding tradeRowWithoutAmount(TulevaFund fund, LocalDate navDate) {
    return new HealthCheckFinding(
        fund,
        UNSETTLED_TRADES,
        NOT_RUN,
        ("SEB's positions carry a trade payables or receivables row without an amount, so it"
                + " cannot be checked against open trades: fund=%s, navDate=%s")
            .formatted(fund.getCode(), navDate));
  }

  private HealthCheckFinding tradeListNotImported(
      TulevaFund fund, LocalDate navDate, BigDecimal tradePayables, BigDecimal tradeReceivables) {
    return new HealthCheckFinding(
        fund,
        UNSETTLED_TRADES,
        NOT_RUN,
        ("SEB's trade list is not imported, so trade payables and receivables cannot be checked"
                + " against open trades: fund=%s, navDate=%s, tradePayables=%s,"
                + " tradeReceivables=%s")
            .formatted(fund.getCode(), navDate, amount(tradePayables), amount(tradeReceivables)));
  }

  private HealthCheckFinding excessUnrecognisedTradesCouldExplain(
      TulevaFund fund, LocalDate navDate, BigDecimal excess, OpenTrades openTrades) {
    return new HealthCheckFinding(
        fund,
        UNSETTLED_TRADES,
        NOT_RUN,
        ("SEB's trade list has rows whose fund is not recognised, and they could explain this"
                + " fund's excess: fund=%s, navDate=%s, excess=%s, unrecognisedClientNames=%s")
            .formatted(
                fund.getCode(), navDate, amount(excess), openTrades.unattributedClientNames()));
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

  private static final Wording PURCHASES =
      new Wording("purchases as owed", "too low", "tradePayables", "openPurchases");
  private static final Wording SALES =
      new Wording("sales as receivable", "too high", "tradeReceivables", "openSales");

  private record Wording(
      String description, String navEffect, String bookedName, String openName) {}
}
