package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

record SebHoldings(
    TulevaFund fund,
    LocalDate positionDate,
    Map<String, BigDecimal> positionUnits,
    OpenTrades openTrades) {

  boolean everyRowIsRead() {
    return openTrades.unattributedClientNames().isEmpty()
        && !openTrades.fundsWithIncompleteRows().contains(fund);
  }

  Optional<BigDecimal> settledUnitsOf(String isin) {
    var openTradesOfTheInstrument =
        openTrades.of(fund).stream().filter(trade -> isin.equals(trade.isin())).toList();
    if (!everyRowIsRead()
        || openTradesOfTheInstrument.stream().map(OpenTrade::quantity).anyMatch(Objects::isNull)) {
      return Optional.empty();
    }
    return Optional.of(
        positionUnits
            .getOrDefault(isin, ZERO)
            .subtract(quantity(openTradesOfTheInstrument, BUY))
            .add(quantity(openTradesOfTheInstrument, SELL)));
  }

  private static BigDecimal quantity(List<OpenTrade> trades, TransactionType side) {
    return trades.stream()
        .filter(trade -> trade.side() == side)
        .map(OpenTrade::quantity)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }
}
