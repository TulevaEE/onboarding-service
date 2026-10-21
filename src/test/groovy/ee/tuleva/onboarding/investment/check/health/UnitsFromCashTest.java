package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.OpenTradeChangesTest.CURRENT;
import static ee.tuleva.onboarding.investment.check.health.OpenTradeChangesTest.PREVIOUS;
import static ee.tuleva.onboarding.investment.check.health.OpenTradeChangesTest.trade;
import static ee.tuleva.onboarding.investment.check.health.OpenTradeChangesTest.trades;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.NAV;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.position.AccountType.UNITS;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class UnitsFromCashTest {

  private static final OpenTradeChanges NO_TRADE_CHANGES =
      OpenTradeChanges.between(TUK00, PREVIOUS, trades(), CURRENT, trades());

  @Test
  void addsTheUnitsThatTheCashReceivedForUnitsPaysFor() {
    var unitsFromCash =
        UnitsFromCash.of(
            TUK00,
            snapshot(CURRENT, "130000.00", "1010000", "2020000.00"),
            snapshot(PREVIOUS, "110000.00", "1000000", "2000000.00"),
            NO_TRADE_CHANGES);

    assertThat(unitsFromCash)
        .hasValueSatisfying(units -> assertThat(units).isEqualTo("1010000.000"));
  }

  @Test
  void takesTheCashOfSettledTradesOutOfTheCashChange() {
    var settledPurchase = trade(TUK00, "DLA1", "IE0005032192", BUY, "100", "20000.00");
    var settledSale = trade(TUK00, "DLA2", "IE00BFG1TM61", SELL, "10", "5000.00");

    var unitsFromCash =
        UnitsFromCash.of(
            TUK00,
            snapshot(CURRENT, "95000.00", "1000000", "2000000.00"),
            snapshot(PREVIOUS, "110000.00", "1000000", "2000000.00"),
            OpenTradeChanges.between(
                TUK00, PREVIOUS, trades(settledPurchase, settledSale), CURRENT, trades()));

    assertThat(unitsFromCash)
        .hasValueSatisfying(units -> assertThat(units).isEqualTo("1000000.000"));
  }

  @Test
  void countsUnitsSoldButNotYetPaidAndUnitsRedeemedButNotYetPaidOut() {
    var today = snapshot(CURRENT, "110000.00", "1002500", "2005000.00");
    today.add(
        position(RECEIVABLES, "Receivables of outstanding units", TUK00.getIsin(), "8000.00"));
    today.add(position(LIABILITY, "Payables of redeemed units", TUK00.getIsin(), "-3000.00"));

    var unitsFromCash =
        UnitsFromCash.of(
            TUK00,
            today,
            snapshot(PREVIOUS, "110000.00", "1000000", "2000000.00"),
            NO_TRADE_CHANGES);

    assertThat(unitsFromCash)
        .hasValueSatisfying(units -> assertThat(units).isEqualTo("1002500.000"));
  }

  @Test
  void leavesReceivablesThatAreNotForTheFundsUnitsOut() {
    var today = snapshot(CURRENT, "110000.00", "1000000", "2000000.00");
    today.add(position(RECEIVABLES, "Dividend receivable", "IE0005032192", "20000.00"));
    today.add(position(RECEIVABLES, "Receivables of outstanding units", TUK75.getIsin(), "500.00"));

    var unitsFromCash =
        UnitsFromCash.of(
            TUK00,
            today,
            snapshot(PREVIOUS, "110000.00", "1000000", "2000000.00"),
            NO_TRADE_CHANGES);

    assertThat(unitsFromCash)
        .hasValueSatisfying(units -> assertThat(units).isEqualTo("1000000.000"));
  }

  @Test
  void hasNoFigureWithoutTheTradeListChanges() {
    var unitsFromCash =
        UnitsFromCash.of(
            TUK00,
            snapshot(CURRENT, "110000.00", "1000000", "2000000.00"),
            snapshot(PREVIOUS, "110000.00", "1000000", "2000000.00"),
            OpenTradeChanges.unavailable("no SEB trade list for 2026-10-19"));

    assertThat(unitsFromCash).isEmpty();
  }

  @Test
  void hasNoFigureWhenATradeListRowNamesNoKnownFund() {
    var unitsFromCash =
        UnitsFromCash.of(
            TUK00,
            snapshot(CURRENT, "110000.00", "1000000", "2000000.00"),
            snapshot(PREVIOUS, "110000.00", "1000000", "2000000.00"),
            new OpenTradeChanges(null, List.of(), List.of(), List.of("Some Other Fund")));

    assertThat(unitsFromCash).isEmpty();
  }

  @Test
  void hasNoFigureWithoutThePreviousDaysPositions() {
    var unitsFromCash =
        UnitsFromCash.of(
            TUK00,
            snapshot(CURRENT, "110000.00", "1000000", "2000000.00"),
            List.of(),
            NO_TRADE_CHANGES);

    assertThat(unitsFromCash).isEmpty();
  }

  @Test
  void hasNoFigureWhenADayHasNoUnitCountOrNoCash() {
    var withoutUnits =
        snapshot(CURRENT, "110000.00", "1000000", "2000000.00").stream()
            .filter(position -> position.getAccountType() != UNITS)
            .toList();
    var withoutCash =
        snapshot(PREVIOUS, "110000.00", "1000000", "2000000.00").stream()
            .filter(position -> position.getAccountType() != CASH)
            .toList();
    var complete = snapshot(PREVIOUS, "110000.00", "1000000", "2000000.00");

    assertThat(UnitsFromCash.of(TUK00, withoutUnits, complete, NO_TRADE_CHANGES)).isEmpty();
    assertThat(UnitsFromCash.of(TUK00, complete, withoutCash, NO_TRADE_CHANGES)).isEmpty();
  }

  @Test
  void hasNoFigureWhenThePreviousDayCannotPriceAUnit() {
    var unitsFromCash =
        UnitsFromCash.of(
            TUK00,
            snapshot(CURRENT, "110000.00", "1000000", "2000000.00"),
            snapshot(PREVIOUS, "110000.00", "0", "2000000.00"),
            NO_TRADE_CHANGES);

    assertThat(unitsFromCash).isEmpty();
  }

  private static ArrayList<FundPosition> snapshot(
      LocalDate date, String cash, String units, String netAssets) {
    var positions = new ArrayList<FundPosition>();
    positions.add(position(date, CASH, "Cash account in SEB Pank", null, cash));
    positions.add(position(date, NAV, "Total", null, netAssets));
    positions.add(
        FundPosition.builder()
            .navDate(date)
            .fund(TUK00)
            .accountType(UNITS)
            .accountName("Total outstanding units:")
            .accountId(TUK00.getIsin())
            .quantity(new BigDecimal(units))
            .build());
    return positions;
  }

  private static FundPosition position(
      AccountType type, String name, @Nullable String accountId, String marketValue) {
    return position(CURRENT, type, name, accountId, marketValue);
  }

  private static FundPosition position(
      LocalDate date,
      AccountType type,
      String name,
      @Nullable String accountId,
      String marketValue) {
    return FundPosition.builder()
        .navDate(date)
        .fund(TUK00)
        .accountType(type)
        .accountName(name)
        .accountId(accountId)
        .marketValue(new BigDecimal(marketValue))
        .build();
  }
}
