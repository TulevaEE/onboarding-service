package ee.tuleva.onboarding.investment.check.health;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OpenTradeChangesTest {

  static final LocalDate PREVIOUS = LocalDate.of(2026, 10, 19);
  static final LocalDate CURRENT = LocalDate.of(2026, 10, 20);

  @Test
  void aTradeTheCurrentListHasAndThePreviousDidNotHasAppeared() {
    var newTrade = trade(TUK75, "DLA2", "IE00BKPTWY98", BUY, "500", "5000.00");

    var changes = OpenTradeChanges.between(TUK75, PREVIOUS, trades(), CURRENT, trades(newTrade));

    assertThat(changes).isEqualTo(new OpenTradeChanges(null, List.of(newTrade), List.of()));
  }

  @Test
  void aTradeThePreviousListHadAndTheCurrentDoesNotHasDisappeared() {
    var settled = trade(TUK75, "DLA1", "IE00BFG1TM61", BUY, "1000", "20000.00");
    var stillOpen = trade(TUK75, "DLA3", "IE00BKPTWY98", BUY, "10", "100.00");

    var changes =
        OpenTradeChanges.between(
            TUK75, PREVIOUS, trades(settled, stillOpen), CURRENT, trades(stillOpen));

    assertThat(changes).isEqualTo(new OpenTradeChanges(null, List.of(), List.of(settled)));
  }

  @Test
  void sumsDisappearedTradesBySide() {
    var changes =
        OpenTradeChanges.between(
            TUK75,
            PREVIOUS,
            trades(
                trade(TUK75, "DLA1", "IE00BFG1TM61", BUY, "100", "1000.10"),
                trade(TUK75, "DLA2", "IE00BKPTWY98", BUY, "10", "200.20"),
                trade(TUK75, "DLA3", "IE0005032192", SELL, "5", "50.00")),
            CURRENT,
            trades());

    assertThat(changes.disappearedTotal(BUY)).isEqualByComparingTo("1200.30");
    assertThat(changes.disappearedTotal(SELL)).isEqualByComparingTo("50.00");
  }

  @Test
  void cannotTellWhatChangedWithoutThePreviousTradeList() {
    var changes = OpenTradeChanges.between(TUK75, PREVIOUS, null, CURRENT, trades());

    assertThat(changes).isEqualTo(OpenTradeChanges.unavailable("no SEB trade list for 2026-10-19"));
  }

  @Test
  void cannotTellWhatChangedWithoutTheCurrentTradeList() {
    var changes = OpenTradeChanges.between(TUK75, PREVIOUS, trades(), CURRENT, null);

    assertThat(changes).isEqualTo(OpenTradeChanges.unavailable("no SEB trade list for 2026-10-20"));
  }

  @Test
  void cannotTellWhatChangedFromAListWithAnIncompleteRowOfTheFund() {
    var changes =
        OpenTradeChanges.between(
            TUK75,
            PREVIOUS,
            trades(),
            CURRENT,
            new OpenTrades(List.of(), Set.of(TUK75), List.of()));

    assertThat(changes)
        .isEqualTo(
            OpenTradeChanges.unavailable(
                "SEB trade list for 2026-10-20 has an incomplete row of this fund"));
  }

  @Test
  void cannotTellWhatChangedFromARowOfTheFundWithoutAQuantity() {
    var withoutQuantity =
        new OpenTrade(
            TUK75,
            BUY,
            new BigDecimal("100.00"),
            "DLA1",
            "IE00BFG1TM61",
            CURRENT,
            null,
            Instant.parse("2026-10-16T09:37:55Z"));

    var changes =
        OpenTradeChanges.between(TUK75, PREVIOUS, trades(withoutQuantity), CURRENT, trades());

    assertThat(changes)
        .isEqualTo(
            OpenTradeChanges.unavailable(
                "SEB trade list for 2026-10-19 has an incomplete row of this fund"));
  }

  @Test
  void anotherFundsIncompleteRowDoesNotStopThisFundsComparison() {
    var changes =
        OpenTradeChanges.between(
            TUK75,
            PREVIOUS,
            trades(),
            CURRENT,
            new OpenTrades(List.of(), Set.of(TUK00), List.of()));

    assertThat(changes.unavailableReason()).isNull();
  }

  @Test
  void aRowNamingNoKnownFundStopsEveryFundsComparison() {
    var changes =
        OpenTradeChanges.between(
            TUK75,
            PREVIOUS,
            trades(),
            CURRENT,
            new OpenTrades(List.of(), Set.of(), List.of("Some Other Fund")));

    assertThat(changes)
        .isEqualTo(
            OpenTradeChanges.unavailable(
                "SEB trade list for 2026-10-20 has rows of no known fund: [Some Other Fund]"));
  }

  static OpenTrades trades(OpenTrade... trades) {
    return new OpenTrades(List.of(trades), Set.of(), List.of());
  }

  static OpenTrade trade(
      TulevaFund fund,
      @Nullable String reference,
      String isin,
      TransactionType side,
      String quantity,
      String total) {
    return new OpenTrade(
        fund,
        side,
        new BigDecimal(total),
        reference,
        isin,
        LocalDate.of(2026, 10, 22),
        new BigDecimal(quantity),
        Instant.parse("2026-10-16T09:37:55Z"));
  }
}
