package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.PendingTrade;
import ee.tuleva.onboarding.investment.transaction.PendingTrades;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PendingTradeChangesTest {

  private static final LocalDate PREVIOUS = LocalDate.of(2026, 10, 19);
  private static final LocalDate CURRENT = LocalDate.of(2026, 10, 20);

  @Test
  void aTradeTheCurrentReportListsAndThePreviousDidNotHasAppeared() {
    var lateConfirmedFundTrade = trade(TUK75, "DLA2", "IE00B", BUY, "74288.82", "1107572");

    var changes =
        PendingTradeChanges.between(
            TUK75, report(PREVIOUS, List.of()), report(CURRENT, List.of(lateConfirmedFundTrade)));

    assertThat(changes.unavailableReason()).isNull();
    assertThat(changes.appeared()).containsExactly(lateConfirmedFundTrade);
    assertThat(changes.disappeared()).isEmpty();
  }

  @Test
  void aTradeThePreviousReportListedAndTheCurrentDoesNotHasDisappeared() {
    var settled = trade(TUK75, "DLA1", "IE00A", BUY, "601870", "3088796.84");
    var stillPending = trade(TUK75, "DLA3", "IE00C", BUY, "10", "100");

    var changes =
        PendingTradeChanges.between(
            TUK75,
            report(PREVIOUS, List.of(settled, stillPending)),
            report(CURRENT, List.of(stillPending)));

    assertThat(changes.appeared()).isEmpty();
    assertThat(changes.disappeared()).containsExactly(settled);
  }

  @Test
  void aTradeStaysTheSameTradeWhenSebAddsItsReferenceOnTheNextReport() {
    var withoutReference = trade(TUK75, null, "IE00A", BUY, "1000", "5000");
    var withReference = trade(TUK75, "DLA1", "IE00A", BUY, "1000.000", "5000");

    var changes =
        PendingTradeChanges.between(
            TUK75,
            report(PREVIOUS, List.of(withoutReference)),
            report(CURRENT, List.of(withReference)));

    assertThat(changes.appeared()).isEmpty();
    assertThat(changes.disappeared()).isEmpty();
  }

  @Test
  void identicalRowsAreMatchedOneForOne() {
    var part = trade(TUK75, "DLA1", "IE00A", BUY, "500", "2500");

    var changes =
        PendingTradeChanges.between(
            TUK75, report(PREVIOUS, List.of(part, part)), report(CURRENT, List.of(part)));

    assertThat(changes.disappeared()).containsExactly(part);
  }

  @Test
  void onlyTheFundsOwnTradesCount() {
    var otherFunds = trade(TUK00, "DLA9", "IE00Z", BUY, "1", "10");

    var changes =
        PendingTradeChanges.between(
            TUK75, report(PREVIOUS, List.of(otherFunds)), report(CURRENT, List.of()));

    assertThat(changes.disappeared()).isEmpty();
  }

  @Test
  void sumsDisappearedTradesBySide() {
    var changes =
        PendingTradeChanges.between(
            TUK75,
            report(
                PREVIOUS,
                List.of(
                    trade(TUK75, "DLA1", "IE00A", BUY, "100", "1000.10"),
                    trade(TUK75, "DLA2", "IE00B", BUY, "10", "200.20"),
                    trade(TUK75, "DLA3", "IE00C", SELL, "5", "50"))),
            report(CURRENT, List.of()));

    assertThat(changes.disappearedTotal(BUY)).isEqualByComparingTo("1200.30");
    assertThat(changes.disappearedTotal(SELL)).isEqualByComparingTo("50");
  }

  @Test
  void cannotTellWhatChangedWithoutBothReports() {
    var changes =
        PendingTradeChanges.between(
            TUK75, new PendingTradeReport(PREVIOUS, null), report(CURRENT, List.of()));

    assertThat(changes.unavailableReason())
        .isEqualTo("no SEB pending transactions report as of 2026-10-19");
  }

  @Test
  void cannotTellWhatChangedFromAReportWithUnusableRowsOfTheFund() {
    var changes =
        PendingTradeChanges.between(
            TUK75,
            report(PREVIOUS, List.of()),
            new PendingTradeReport(CURRENT, new PendingTrades(List.of(), Map.of(TUK75, 2), 0)));

    assertThat(changes.unavailableReason())
        .isEqualTo("SEB pending transactions report as of 2026-10-20 has 2 unusable rows");
  }

  @Test
  void anotherFundsUnusableRowDoesNotStopThisFundsComparison() {
    var changes =
        PendingTradeChanges.between(
            TUK75,
            report(PREVIOUS, List.of()),
            new PendingTradeReport(CURRENT, new PendingTrades(List.of(), Map.of(TUK00, 1), 0)));

    assertThat(changes.unavailableReason()).isNull();
  }

  @Test
  void aRowNoFundCanBeNamedForStopsEveryFundsComparison() {
    var changes =
        PendingTradeChanges.between(
            TUK75,
            report(PREVIOUS, List.of()),
            new PendingTradeReport(CURRENT, new PendingTrades(List.of(), Map.of(), 1)));

    assertThat(changes.unavailableReason())
        .isEqualTo("SEB pending transactions report as of 2026-10-20 has 1 unusable rows");
  }

  @Test
  void aTradeKeepsItsIdentityWhenSebRevisesItUnderTheSameReference() {
    var estimated = trade(TUK75, "DLA1", "IE00A", BUY, "1000", "5000");
    var revised = trade(TUK75, "DLA1", "IE00A", BUY, "1000.37", "5001.85");

    var changes =
        PendingTradeChanges.between(
            TUK75, report(PREVIOUS, List.of(estimated)), report(CURRENT, List.of(revised)));

    assertThat(changes.appeared()).isEmpty();
    assertThat(changes.disappeared()).isEmpty();
  }

  @Test
  void twoTradesWithDifferentReferencesAreDifferentTradesEvenWithTheSameFigures() {
    var first = trade(TUK75, "DLA1", "IE00A", BUY, "500", "2500");
    var second = trade(TUK75, "DLA2", "IE00A", BUY, "500", "2500");

    var changes =
        PendingTradeChanges.between(
            TUK75, report(PREVIOUS, List.of(first)), report(CURRENT, List.of(second)));

    assertThat(changes.appeared()).containsExactly(second);
    assertThat(changes.disappeared()).containsExactly(first);
  }

  static PendingTradeReport report(LocalDate asOf, List<PendingTrade> trades) {
    return new PendingTradeReport(asOf, new PendingTrades(trades, Map.of(), 0));
  }

  static PendingTrade trade(
      TulevaFund fund,
      @Nullable String reference,
      String isin,
      TransactionType side,
      String quantity,
      String total) {
    return new PendingTrade(
        fund,
        reference,
        isin,
        side,
        new BigDecimal(quantity),
        new BigDecimal(total),
        Instant.parse("2026-10-16T09:37:55Z"),
        LocalDate.of(2026, 10, 20));
  }
}
