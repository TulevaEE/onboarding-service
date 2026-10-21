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

    var missing = trades(settled, stillOpen).missingFrom(trades(stillOpen), TUK75);

    assertThat(missing).containsExactly(settled);
  }

  @Test
  void aTradeStaysTheSameTradeWhenSebAddsItsReferenceOnTheNextList() {
    var withoutReference = trade(TUK75, null, "1000", "5000.00");
    var withReference = trade(TUK75, "DLA1", "1000.000", "5000.00");

    assertThat(trades(withoutReference).missingFrom(trades(withReference), TUK75)).isEmpty();
    assertThat(trades(withReference).missingFrom(trades(withoutReference), TUK75)).isEmpty();
  }

  @Test
  void aTradeKeepsItsIdentityWhenSebRevisesItUnderTheSameReference() {
    var estimated = trade(TUK75, "DLA1", "1000", "5000.00");
    var revised = trade(TUK75, "DLA1", "1000.37", "5001.85");

    assertThat(trades(estimated).missingFrom(trades(revised), TUK75)).isEmpty();
  }

  @Test
  void twoTradesWithDifferentReferencesAreDifferentTradesEvenWithTheSameFigures() {
    var first = trade(TUK75, "DLA1", "500", "2500.00");
    var second = trade(TUK75, "DLA2", "500", "2500.00");

    assertThat(trades(first).missingFrom(trades(second), TUK75)).containsExactly(first);
  }

  @Test
  void twoTradesWithoutReferencesDifferWhenTheirQuantitiesDiffer() {
    var first = trade(TUK75, null, "500", "2500.00");
    var second = trade(TUK75, null, "501", "2500.00");

    assertThat(trades(first).missingFrom(trades(second), TUK75)).containsExactly(first);
  }

  @Test
  void identicalRowsAreMatchedOneForOne() {
    var part = trade(TUK75, "DLA1", "500", "2500.00");

    assertThat(trades(part, part).missingFrom(trades(part), TUK75)).containsExactly(part);
  }

  @Test
  void onlyTheFundsOwnTradesCount() {
    var otherFunds = trade(TUK00, "DLA9", "1", "10.00");

    assertThat(trades(otherFunds).missingFrom(trades(), TUK75)).isEmpty();
  }

  private static OpenTrades trades(OpenTrade... trades) {
    return new OpenTrades(List.of(trades), Set.of(), List.of());
  }

  private static OpenTrade trade(
      TulevaFund fund, @Nullable String reference, String quantity, String total) {
    return new OpenTrade(
        fund,
        BUY,
        new BigDecimal(total),
        reference,
        "IE00BFG1TM61",
        LocalDate.of(2026, 10, 22),
        new BigDecimal(quantity),
        Instant.parse("2026-10-16T09:37:55Z"));
  }
}
