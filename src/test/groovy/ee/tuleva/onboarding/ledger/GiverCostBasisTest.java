package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.GiverCostBasis.replay;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_COUNT_UPDATE;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_TRANSFER;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GiverCostBasisTest {

  @Test
  void aHoldingWithNoUnitsLeftHasNoCostToShareOut() {
    var costBasis = new GiverCostBasis(ZERO, new BigDecimal("10.00"));

    assertThatThrownBy(() -> costBasis.costOf(ONE)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aRedemptionTakesWhatTheUnitsCostOnAverageAcrossPurchasesAtDifferentPrices() {
    var costBasis =
        replay(
            List.of(
                subscription("10.00000", "100.00"),
                subscription("10.00000", "300.00"),
                redemption("-10.00000")));

    assertThat(costBasis).isEqualTo(costBasis("10.00000", "200.00"));
  }

  @Test
  void repeatedRedemptionsTakeTheirShareInWholeCentsUntilNoCostIsLeft() {
    var history =
        List.of(
            subscription("1.00000", "1.00"),
            redemption("-0.33333"),
            redemption("-0.33333"),
            redemption("-0.33333"));

    assertThat(replay(history.subList(0, 2))).isEqualTo(costBasis("0.66667", "0.67"));
    assertThat(replay(history.subList(0, 3))).isEqualTo(costBasis("0.33334", "0.34"));
    assertThat(replay(history)).isEqualTo(costBasis("0.00001", "0.00"));
  }

  @Test
  void anAdjustmentThatLeavesNoUnitsLeavesNoCostEither() {
    var costBasis =
        replay(List.of(subscription("10.00000", "100.00"), adjustment("-10.00000", "0.00")));

    assertThat(costBasis).isEqualTo(costBasis("0.00000", "0.00"));
  }

  @Test
  void unitsAdjustedInWithoutACostDiluteWhatTheHoldingCostOnAverage() {
    var costBasis =
        replay(List.of(subscription("10.00000", "100.00"), adjustment("10.00000", "0.00")));

    assertThat(costBasis).isEqualTo(costBasis("20.00000", "100.00"));
    assertThat(costBasis.costOf(new BigDecimal("10.00000"))).isEqualTo(new BigDecimal("50.00"));
  }

  @Test
  void unitsThatArriveAsATransferBringTheirCostWithThem() {
    var costBasis =
        replay(List.of(subscription("10.00000", "100.00"), transferIn("5.00000", "50.00")));

    assertThat(costBasis).isEqualTo(costBasis("15.00000", "150.00"));
  }

  @Test
  void unitsThatMovedUnderATypeTheirCostCannotBeReplayedFromAreRefused() {
    var history = List.of(change(UNIT_COUNT_UPDATE, "10.00000", "0.00"));

    assertThatThrownBy(() -> replay(history)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void theWholeCostFollowsWhenEveryRemainingUnitMoves() {
    var costBasis = costBasis("3.00000", "10.01");

    assertThat(costBasis.costOf(new BigDecimal("3.00000"))).isEqualTo(new BigDecimal("10.01"));
  }

  private static GiverCostBasis costBasis(String units, String cost) {
    return new GiverCostBasis(new BigDecimal(units), new BigDecimal(cost));
  }

  private static UnitHoldingChange subscription(String units, String cost) {
    return change(FUND_SUBSCRIPTION, units, cost);
  }

  private static UnitHoldingChange redemption(String units) {
    return change(REDEMPTION_REQUEST, units, "0.00");
  }

  private static UnitHoldingChange adjustment(String units, String cost) {
    return change(ADJUSTMENT, units, cost);
  }

  private static UnitHoldingChange transferIn(String units, String cost) {
    return change(UNIT_TRANSFER, units, cost);
  }

  private static UnitHoldingChange change(
      TransactionType transactionType, String units, String cost) {
    return new UnitHoldingChange(
        randomUUID(), transactionType, new BigDecimal(units), new BigDecimal(cost));
  }
}
