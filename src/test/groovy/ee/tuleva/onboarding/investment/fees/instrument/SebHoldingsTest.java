package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class SebHoldingsTest {

  private static final String ISIN = "ZZ0000000001";
  private static final String OTHER_ISIN = "ZZ0000000002";
  private static final LocalDate POSITION_DATE = LocalDate.of(2026, 11, 20);

  @Test
  void settledUnitsAreTheTradeDatePositionLessUnsettledBuysPlusUnsettledSells() {
    var holdings =
        holdings(
            Map.of(ISIN, new BigDecimal("1000.000")),
            new OpenTrades(
                List.of(trade(TUK75, BUY, ISIN, "120.000"), trade(TUK75, SELL, ISIN, "30.000")),
                Set.of(),
                List.of()));

    assertThat(holdings.settledUnitsOf(ISIN))
        .hasValueSatisfying(units -> assertThat(units).isEqualByComparingTo("910.000"));
  }

  @Test
  void anotherInstrumentsOrAnotherFundsOpenTradesLeaveTheUnitsAsTheyAre() {
    var holdings =
        holdings(
            Map.of(ISIN, new BigDecimal("1000.000")),
            new OpenTrades(
                List.of(
                    trade(TUK75, BUY, OTHER_ISIN, "120.000"), trade(TUK00, BUY, ISIN, "50.000")),
                Set.of(),
                List.of()));

    assertThat(holdings.settledUnitsOf(ISIN))
        .hasValueSatisfying(units -> assertThat(units).isEqualByComparingTo("1000.000"));
  }

  @Test
  void anUnsettledSaleOfAnInstrumentNoLongerInThePositionStillCountsItsUnits() {
    var holdings =
        holdings(
            Map.of(),
            new OpenTrades(List.of(trade(TUK75, SELL, ISIN, "250.000")), Set.of(), List.of()));

    assertThat(holdings.settledUnitsOf(ISIN))
        .hasValueSatisfying(units -> assertThat(units).isEqualByComparingTo("250.000"));
  }

  @Test
  void anOpenTradeWithoutAQuantityLeavesTheSettledUnitsUnknown() {
    var holdings =
        holdings(
            Map.of(ISIN, new BigDecimal("1000.000")),
            new OpenTrades(List.of(trade(TUK75, BUY, ISIN, null)), Set.of(), List.of()));

    assertThat(holdings.settledUnitsOf(ISIN)).isEmpty();
  }

  @Test
  void aReportWithTheFundsRowsUnreadLeavesTheSettledUnitsUnknown() {
    var holdings =
        holdings(
            Map.of(ISIN, new BigDecimal("1000.000")),
            new OpenTrades(List.of(), Set.of(TUK75), List.of()));

    assertThat(holdings.everyRowIsRead()).isFalse();
    assertThat(holdings.settledUnitsOf(ISIN)).isEmpty();
  }

  @Test
  void aReportWithARowNoFundAnswersToLeavesTheSettledUnitsUnknown() {
    var holdings =
        holdings(
            Map.of(ISIN, new BigDecimal("1000.000")),
            new OpenTrades(List.of(), Set.of(), List.of("Unrecognised client")));

    assertThat(holdings.everyRowIsRead()).isFalse();
    assertThat(holdings.settledUnitsOf(ISIN)).isEmpty();
  }

  private static SebHoldings holdings(Map<String, BigDecimal> positionUnits, OpenTrades trades) {
    return new SebHoldings(TUK75, POSITION_DATE, positionUnits, trades);
  }

  private static OpenTrade trade(
      TulevaFund fund, TransactionType side, String isin, @Nullable String quantity) {
    return new OpenTrade(
        fund,
        side,
        new BigDecimal("1000.00"),
        null,
        isin,
        POSITION_DATE.plusDays(2),
        quantity == null ? null : new BigDecimal(quantity),
        null);
  }
}
