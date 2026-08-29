package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SellSafetySpillTest {

  private static FundTransactionInput input(
      List<PositionSnapshot> positions, BigDecimal threshold) {
    return FundTransactionInput.builder()
        .fund(TUV100)
        .positions(positions)
        .modelWeights(List.of())
        .grossPortfolioValue(new BigDecimal("1000000"))
        .cashBuffer(ZERO)
        .liabilities(ZERO)
        .freeCash(ZERO)
        .minTransactionThreshold(threshold)
        .build();
  }

  @Test
  void opensNewSellWhenResidualAtLeastThresholdTolerance() {
    var threshold = new BigDecimal("5000");
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("100000")),
            new PositionSnapshot("B", new BigDecimal("50000")));
    var sells = List.of(new BigDecimal("100000"), ZERO);
    var scores = List.of(ZERO, ZERO);

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("104999.99"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("100000"));
    assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("4999.99"));
  }

  @Test
  void leavesHeadroomUntouchedWhenResidualOneCentBelowThresholdTolerance() {
    var threshold = new BigDecimal("5000");
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("100000")),
            new PositionSnapshot("B", new BigDecimal("50000")));
    var sells = List.of(new BigDecimal("100000"), ZERO);
    var scores = List.of(ZERO, ZERO);

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("104999.98"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("100000"));
    assertThat(result.get(1)).isEqualByComparingTo(ZERO);
  }

  @Test
  void skipsOpeningWhenNoHeadroomRemainsAnywhere() {
    var threshold = new BigDecimal("5000");
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("50000")),
            new PositionSnapshot("B", new BigDecimal("60000")));
    var sells = List.of(new BigDecimal("50000"), new BigDecimal("60000"));
    var scores = List.of(ZERO, ZERO);

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("111000"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("50000"));
    assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("60000"));
  }

  @Test
  void topsUpAlreadyActiveSellWhenResidualExceedsMinMeaningfulAmount() {
    var threshold = new BigDecimal("5000");
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("100000")),
            new PositionSnapshot("B", new BigDecimal("50000")));
    var sells = List.of(new BigDecimal("90000"), new BigDecimal("50000"));
    var scores = List.of(ZERO, ZERO);

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("140000.02"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("90000.02"));
    assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("50000"));
  }

  @Test
  void skipsTopUpWhenResidualExactlyAtMinMeaningfulAmount() {
    var threshold = new BigDecimal("5000");
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("100000")),
            new PositionSnapshot("B", new BigDecimal("50000")));
    var sells = List.of(new BigDecimal("90000"), new BigDecimal("50000"));
    var scores = List.of(ZERO, ZERO);

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("140000.01"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("90000"));
    assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("50000"));
  }

  @Test
  void excludesSellExactlyAtMinMeaningfulAmountFromTopUpEligibility() {
    var threshold = new BigDecimal("5000");
    var positions = List.of(new PositionSnapshot("A", new BigDecimal("100000")));
    var sells = List.of(new BigDecimal("0.01"));
    var scores = List.of(ZERO);

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("0.03"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  @Test
  void liquidatesTrappedOddLotsInDescendingScoreOrderAndCanOvershootTheBudget() {
    var threshold = new BigDecimal("5000");
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("3000")),
            new PositionSnapshot("C", new BigDecimal("2000")),
            new PositionSnapshot("B", new BigDecimal("50000")));
    var sells = List.of(ZERO, ZERO, new BigDecimal("50000"));
    var scores = List.of(new BigDecimal("10"), new BigDecimal("20"), ZERO);

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("52500"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("3000"));
    assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("2000"));
    assertThat(result.get(2)).isEqualByComparingTo(new BigDecimal("50000"));
  }

  @Test
  void excludesRemainderExactlyAtMinMeaningfulAmountFromOddLots() {
    var threshold = new BigDecimal("5000");
    var positions = List.of(new PositionSnapshot("A", new BigDecimal("0.01")));
    var sells = List.of(ZERO);
    var scores = List.of(new BigDecimal("10"));

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("50"));

    assertThat(result.get(0)).isEqualByComparingTo(ZERO);
  }

  @Test
  void includesRemainderOneCentAboveMinMeaningfulAmountInOddLots() {
    var threshold = new BigDecimal("5000");
    var positions = List.of(new PositionSnapshot("A", new BigDecimal("0.02")));
    var sells = List.of(ZERO);
    var scores = List.of(new BigDecimal("10"));

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("50"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("0.02"));
  }

  @Test
  void excludesRemainderExactlyAtThresholdToleranceFromOddLots() {
    var threshold = new BigDecimal("5000");
    var positions = List.of(new PositionSnapshot("A", new BigDecimal("4999.99")));
    var sells = List.of(ZERO);
    var scores = List.of(new BigDecimal("10"));

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("0.02"));

    assertThat(result.get(0)).isEqualByComparingTo(ZERO);
  }

  @Test
  void includesRemainderOneCentBelowThresholdToleranceInOddLots() {
    var threshold = new BigDecimal("5000");
    var positions = List.of(new PositionSnapshot("A", new BigDecimal("4999.98")));
    var sells = List.of(ZERO);
    var scores = List.of(new BigDecimal("10"));

    var result =
        SellSafetySpill.applySellSafetySpill(
            sells, input(positions, threshold), scores, new BigDecimal("0.02"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("4999.98"));
  }
}
