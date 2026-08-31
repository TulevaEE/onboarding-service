package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.PositionLimitSnapshot;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeadroomRedistributionTest {

  private static FundTransactionInput input(
      List<PositionSnapshot> positions,
      Map<String, PositionLimitSnapshot> positionLimits,
      BigDecimal threshold) {
    return FundTransactionInput.builder()
        .fund(TUV100)
        .positions(positions)
        .modelWeights(List.of())
        .grossPortfolioValue(new BigDecimal("1000000"))
        .cashBuffer(ZERO)
        .liabilities(ZERO)
        .freeCash(ZERO)
        .minTransactionThreshold(threshold)
        .positionLimits(positionLimits)
        .build();
  }

  @Test
  void hardLimitHeadroomReturnsNullWhenNoLimitConfigured() {
    var position = new PositionSnapshot("A", new BigDecimal("100000"));
    var in = input(List.of(position), Map.of(), new BigDecimal("5000"));

    assertThat(HeadroomRedistribution.hardLimitHeadroom(in, position)).isNull();
  }

  @Test
  void hardLimitHeadroomComputesRemainingCapacityBelowHardLimit() {
    var position = new PositionSnapshot("A", new BigDecimal("500000"));
    var limits =
        Map.of("A", new PositionLimitSnapshot(new BigDecimal("0.50"), new BigDecimal("0.60")));
    var in = input(List.of(position), limits, new BigDecimal("5000"));

    var headroom = HeadroomRedistribution.hardLimitHeadroom(in, position);

    assertThat(headroom).isEqualByComparingTo(new BigDecimal("99900"));
  }

  @Test
  void runnerScoresExcludesRunnerWhoseRemainingHeadroomIsOneCentBelowThreshold() {
    var position = new PositionSnapshot("A", new BigDecimal("500000"));
    var limits =
        Map.of("A", new PositionLimitSnapshot(new BigDecimal("0.10"), new BigDecimal("0.6001")));
    var in = input(List.of(position), limits, new BigDecimal("5000"));
    var capped = new BigDecimal[] {new BigDecimal("95000.01")};

    var scores = HeadroomRedistribution.runnerScores(in, List.of(new BigDecimal("100")), capped);

    assertThat(scores.get(0)).isEqualByComparingTo(ZERO);
  }

  @Test
  void runnerScoresIncludesRunnerWhoseRemainingHeadroomExactlyMeetsThreshold() {
    var position = new PositionSnapshot("A", new BigDecimal("500000"));
    var limits =
        Map.of("A", new PositionLimitSnapshot(new BigDecimal("0.10"), new BigDecimal("0.6001")));
    var in = input(List.of(position), limits, new BigDecimal("5000"));
    var capped = new BigDecimal[] {new BigDecimal("95000")};

    var scores = HeadroomRedistribution.runnerScores(in, List.of(new BigDecimal("100")), capped);

    assertThat(scores.get(0)).isEqualByComparingTo(new BigDecimal("100"));
  }

  @Test
  void waterFillExcessAcrossRunnersLeavesUndistributedRemainderWhenSoleRunnerHitsHeadroom() {
    var position = new PositionSnapshot("A", new BigDecimal("500000"));
    var limits =
        Map.of("A", new PositionLimitSnapshot(new BigDecimal("0.10"), new BigDecimal("0.5051")));
    var in = input(List.of(position), limits, new BigDecimal("100"));
    var capped = new BigDecimal[] {ZERO};

    var remaining =
        HeadroomRedistribution.waterFillExcessAcrossRunners(
            in, List.of(new BigDecimal("1")), capped, new BigDecimal("6000"));

    assertThat(capped[0]).isEqualByComparingTo(new BigDecimal("5000"));
    assertThat(remaining).isEqualByComparingTo(new BigDecimal("1000"));
  }

  @Test
  void waterFillExcessAcrossRunnersRedistributesLeftoverToUnlimitedRunnerAfterCapping() {
    var positions =
        List.of(
            new PositionSnapshot("A", new BigDecimal("500000")),
            new PositionSnapshot("B", new BigDecimal("200000")));
    var limits =
        Map.of("A", new PositionLimitSnapshot(new BigDecimal("0.10"), new BigDecimal("0.5031")));
    var in = input(positions, limits, new BigDecimal("100"));
    var capped = new BigDecimal[] {ZERO, ZERO};

    var remaining =
        HeadroomRedistribution.waterFillExcessAcrossRunners(
            in, List.of(new BigDecimal("1"), new BigDecimal("1")), capped, new BigDecimal("6000"));

    assertThat(capped[0]).isEqualByComparingTo(new BigDecimal("3000"));
    assertThat(capped[1]).isEqualByComparingTo(new BigDecimal("3000"));
    assertThat(remaining).isEqualByComparingTo(ZERO);
  }
}
