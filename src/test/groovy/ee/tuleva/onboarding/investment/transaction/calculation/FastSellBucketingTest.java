package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FastSellBucketingTest {

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
  void sellFastBucketFullyLiquidatesWhenTargetExactlyEqualsTotalFastValue() {
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("50000")),
            new PositionSnapshot("B", new BigDecimal("30000")));
    var in = input(positions, new BigDecimal("5000"));
    var results = new BigDecimal[] {ZERO, ZERO};

    FastSellBucketing.sellFastBucket(
        in,
        List.of(0, 1),
        List.of(new BigDecimal("10000"), new BigDecimal("10000")),
        new BigDecimal("80000"),
        new BigDecimal("80000"),
        results);

    assertThat(results[0]).isEqualByComparingTo(new BigDecimal("-50000"));
    assertThat(results[1]).isEqualByComparingTo(new BigDecimal("-30000"));
  }

  @Test
  void sellFastBucketOneCentBelowTotalFastValueDistributesByOverweightInstead() {
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("50000")),
            new PositionSnapshot("B", new BigDecimal("30000")));
    var in = input(positions, new BigDecimal("5000"));
    var results = new BigDecimal[] {ZERO, ZERO};

    FastSellBucketing.sellFastBucket(
        in,
        List.of(0, 1),
        List.of(new BigDecimal("10000"), new BigDecimal("10000")),
        new BigDecimal("79999.99"),
        new BigDecimal("80000"),
        results);

    assertThat(results[0]).isEqualByComparingTo(new BigDecimal("-50000"));
    assertThat(results[1]).isEqualByComparingTo(new BigDecimal("-29999.99"));
  }

  @Test
  void distributeSellByOverweightFallsBackToHeadroomScoresWhenAllOverweightScoresAreZero() {
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("50000")),
            new PositionSnapshot("B", new BigDecimal("30000")));
    var in = input(positions, new BigDecimal("5000"));
    var results = new BigDecimal[] {ZERO, ZERO};

    FastSellBucketing.distributeSellByOverweight(
        in,
        List.of(0, 1),
        List.of(new BigDecimal("50000"), new BigDecimal("30000")),
        new BigDecimal("20000"),
        results);

    assertThat(results[0]).isEqualByComparingTo(new BigDecimal("-12500"));
    assertThat(results[1]).isEqualByComparingTo(new BigDecimal("-7500"));
  }

  @Test
  void distributeSellByOverweightUsesOverweightScoresWhenSumIsExactlyAtMinMeaningfulAmount() {
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("50000")),
            new PositionSnapshot("B", new BigDecimal("30000")));
    var in = input(positions, new BigDecimal("5000"));
    var results = new BigDecimal[] {ZERO, ZERO};

    FastSellBucketing.distributeSellByOverweight(
        in,
        List.of(0, 1),
        List.of(new BigDecimal("49999.99"), new BigDecimal("30000")),
        new BigDecimal("20000"),
        results);

    assertThat(results[0]).isEqualByComparingTo(new BigDecimal("-20000"));
    assertThat(results[1]).isEqualByComparingTo(ZERO);
  }
}
