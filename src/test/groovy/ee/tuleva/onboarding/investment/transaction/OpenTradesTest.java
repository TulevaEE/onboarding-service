package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OpenTradesTest {

  @Test
  void aTradeTheLaterListNoLongerHasIsMissingFromIt() {
    var settled = trade(TUK75, "DLA1", "1000", "5000.00");
    var stillOpen = trade(TUK75, "DLA2", "10", "100.00");

    var missing = trades(settled, stillOpen).comparedWith(trades(stillOpen), TUK75).onlyEarlier();

    assertThat(missing).containsExactly(settled);
  }

  @Test
  void aTradeStaysTheSameTradeWhenSebAddsItsReferenceOnTheNextList() {
    var withoutReference = trade(TUK75, null, "1000", "5000.00");
    var withReference = trade(TUK75, "DLA1", "1000.000", "5000.00");

    assertThat(trades(withoutReference).comparedWith(trades(withReference), TUK75))
        .isEqualTo(new OpenTrades.Difference(List.of(), List.of()));
  }

  @Test
  void aTradeKeepsItsIdentityWhenSebRevisesItUnderTheSameReference() {
    var estimated = trade(TUK75, "DLA1", "1000", "5000.00");
    var revised = trade(TUK75, "DLA1", "1000.37", "5001.85");

    assertThat(trades(estimated).comparedWith(trades(revised), TUK75))
        .isEqualTo(new OpenTrades.Difference(List.of(), List.of()));
  }

  @Test
  void twoTradesWithDifferentReferencesAreDifferentTradesEvenWithTheSameFigures() {
    var first = trade(TUK75, "DLA1", "500", "2500.00");
    var second = trade(TUK75, "DLA2", "500", "2500.00");

    assertThat(trades(first).comparedWith(trades(second), TUK75))
        .isEqualTo(new OpenTrades.Difference(List.of(first), List.of(second)));
  }

  @Test
  void twoTradesWithoutReferencesDifferWhenTheirQuantitiesDiffer() {
    var first = trade(TUK75, null, "500", "2500.00");
    var second = trade(TUK75, null, "501", "2500.00");

    assertThat(trades(first).comparedWith(trades(second), TUK75))
        .isEqualTo(new OpenTrades.Difference(List.of(first), List.of(second)));
  }

  @Test
  void aTradeThatLostItsReferenceDoesNotTakeTheMatchOfATradeThatKeptOne() {
    var first = trade(TUK75, "DLA1", "500", "5000.00");
    var second = trade(TUK75, "DLA2", "500", "5000.00");
    var secondWithoutReference = trade(TUK75, null, "500", "5000.00");
    var unchanged = new OpenTrades.Difference(List.of(), List.of());

    assertThat(trades(first, second).comparedWith(trades(secondWithoutReference, first), TUK75))
        .isEqualTo(unchanged);
    assertThat(trades(first, second).comparedWith(trades(first, secondWithoutReference), TUK75))
        .isEqualTo(unchanged);
    assertThat(trades(second, first).comparedWith(trades(secondWithoutReference, first), TUK75))
        .isEqualTo(unchanged);
  }

  @Test
  void pairsAsManyTradesAsTheirReferencesAllowWhateverTheRowOrder() {
    var withoutReference = trade(TUK75, null, "500", "5000.00");
    var lostItsReference = trade(TUK75, "DLA1", "500", "5000.00");
    var gainedItsReference = trade(TUK75, "DLA2", "500", "5000.00");
    var unchanged = new OpenTrades.Difference(List.of(), List.of());

    for (var earlier :
        List.of(
            List.of(withoutReference, lostItsReference),
            List.of(lostItsReference, withoutReference))) {
      for (var later :
          List.of(
              List.of(withoutReference, gainedItsReference),
              List.of(gainedItsReference, withoutReference))) {
        assertThat(listOf(earlier).comparedWith(listOf(later), TUK75)).isEqualTo(unchanged);
        assertThat(listOf(later).comparedWith(listOf(earlier), TUK75)).isEqualTo(unchanged);
      }
    }
  }

  @Test
  void leavesTheSameTradesUnpairedWhateverTheRowOrder() {
    var first = trade(TUK75, "DLA1", "500", "5000.00");
    var second = trade(TUK75, "DLA2", "500", "5001.00");
    var withoutReference = trade(TUK75, null, "500", "5000.50");

    var difference = trades(first, second).comparedWith(trades(withoutReference), TUK75);

    assertThat(trades(second, first).comparedWith(trades(withoutReference), TUK75))
        .isEqualTo(difference);
    assertThat(difference.onlyEarlier()).hasSize(1);
  }

  @Test
  void pairsTwoPartsOfOneReferenceSoThatTheRowWithoutAReferenceStillPairs() {
    var largerPart = trade(TUK75, "DLA1", "200", "2000.00");
    var smallerPart = trade(TUK75, "DLA1", "100", "1000.00");
    var revisedPart = trade(TUK75, "DLA1", "100", "1000.00");
    var withoutReference = trade(TUK75, null, "100", "1000.00");
    var unchanged = new OpenTrades.Difference(List.of(), List.of());

    for (var earlier :
        List.of(List.of(largerPart, smallerPart), List.of(smallerPart, largerPart))) {
      for (var later :
          List.of(List.of(revisedPart, withoutReference), List.of(withoutReference, revisedPart))) {
        assertThat(listOf(earlier).comparedWith(listOf(later), TUK75)).isEqualTo(unchanged);
        assertThat(listOf(later).comparedWith(listOf(earlier), TUK75)).isEqualTo(unchanged);
      }
    }
  }

  @Test
  void leavesTheSameRowUnpairedWhenRowsDifferOnlyInSettlementDate() {
    var settlingFirst = trade(TUK75, null, "500", "5000.00", LocalDate.of(2026, 10, 21));
    var settlingLater = trade(TUK75, null, "500", "5000.00", LocalDate.of(2026, 10, 22));
    var later = trades(trade(TUK75, null, "500", "5000.00", LocalDate.of(2026, 10, 22)));

    assertThat(trades(settlingFirst, settlingLater).comparedWith(later, TUK75))
        .isEqualTo(trades(settlingLater, settlingFirst).comparedWith(later, TUK75));
  }

  @Test
  void identicalRowsAreMatchedOneForOne() {
    var part = trade(TUK75, "DLA1", "500", "2500.00");

    assertThat(trades(part, part).comparedWith(trades(part), TUK75))
        .isEqualTo(new OpenTrades.Difference(List.of(part), List.of()));
  }

  @Test
  void onlyTheFundsOwnTradesCount() {
    var otherFunds = trade(TUK00, "DLA9", "1", "10.00");

    assertThat(trades(otherFunds).comparedWith(trades(), TUK75))
        .isEqualTo(new OpenTrades.Difference(List.of(), List.of()));
  }

  private static OpenTrades trades(OpenTrade... trades) {
    return listOf(List.of(trades));
  }

  private static OpenTrades listOf(List<OpenTrade> trades) {
    return new OpenTrades(trades, Set.of(), List.of());
  }

  private static OpenTrade trade(
      TulevaFund fund, @Nullable String reference, String quantity, String total) {
    return trade(fund, reference, quantity, total, LocalDate.of(2026, 10, 22));
  }

  private static OpenTrade trade(
      TulevaFund fund,
      @Nullable String reference,
      String quantity,
      String total,
      LocalDate settlementDate) {
    return new OpenTrade(
        fund,
        BUY,
        new BigDecimal(total),
        reference,
        "IE00BFG1TM61",
        settlementDate,
        new BigDecimal(quantity),
        Instant.parse("2026-10-16T09:37:55Z"));
  }
}
