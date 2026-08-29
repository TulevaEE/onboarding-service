package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class TradeDistributionTest {

  @Test
  void distributeAmountWithThreshold_eliminatesSmallestOneAtATime() {
    var scores =
        List.of(new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"));
    var amount = new BigDecimal("15000");
    var threshold = new BigDecimal("5000");

    var result = TradeDistribution.distributeAmountWithThreshold(scores, amount, threshold);

    var nonZero = result.stream().filter(v -> v.compareTo(ZERO) > 0).toList();
    assertThat(nonZero).hasSize(3);
    nonZero.forEach(v -> assertThat(v).isGreaterThanOrEqualTo(new BigDecimal("4999")));
    assertThat(result.stream().reduce(ZERO, BigDecimal::add))
        .isCloseTo(amount, Offset.offset(BigDecimal.ONE));
  }

  @Test
  void distributeAmountWithThreshold_zeroScoreGetsNoAllocation() {
    var scores = List.of(ZERO, new BigDecimal("1"), new BigDecimal("1"));
    var result =
        TradeDistribution.distributeAmountWithThreshold(
            scores, new BigDecimal("200"), new BigDecimal("50"));

    assertThat(result.get(0)).isEqualByComparingTo(ZERO);
    assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(result.get(2)).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  void distributeAmountWithThreshold_allocationExactlyAtThresholdToleranceIsAccepted() {
    var scores = List.of(new BigDecimal("1"), new BigDecimal("1"));
    var result =
        TradeDistribution.distributeAmountWithThreshold(
            scores, new BigDecimal("199.98"), new BigDecimal("100"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("99.99"));
    assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("99.99"));
  }

  @Test
  void distributeAmountWithThreshold_tieEliminatesFirstIndexOnceBelowTolerance() {
    var scores = List.of(new BigDecimal("1"), new BigDecimal("1"));
    var result =
        TradeDistribution.distributeAmountWithThreshold(
            scores, new BigDecimal("199.96"), new BigDecimal("100"));

    assertThat(result.get(0)).isEqualByComparingTo(ZERO);
    assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("199.96"));
  }

  @Test
  void distributeCapped_singlePositionCapBelowThresholdToleranceStaysAtZero() {
    var result =
        TradeDistribution.distributeCapped(
            List.of(new BigDecimal("1")),
            new BigDecimal("100"),
            new BigDecimal("100"),
            List.of(new BigDecimal("99.98")));

    assertThat(result[0]).isEqualByComparingTo(ZERO);
  }

  @Test
  void distributeCapped_singlePositionCapExactlyAtThresholdToleranceGetsCappedImmediately() {
    var result =
        TradeDistribution.distributeCapped(
            List.of(new BigDecimal("1")),
            new BigDecimal("100"),
            new BigDecimal("100"),
            List.of(new BigDecimal("99.99")));

    assertThat(result[0]).isEqualByComparingTo(new BigDecimal("99.99"));
  }

  @Test
  void distributeCapped_clearsActiveSetAfterFullyFixingEveryone() {
    var result =
        TradeDistribution.distributeCapped(
            List.of(new BigDecimal("1"), new BigDecimal("1")),
            new BigDecimal("100"),
            ZERO,
            List.of(new BigDecimal("1000"), new BigDecimal("1000")));

    assertThat(result[0]).isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(result[1]).isEqualByComparingTo(new BigDecimal("50.00"));
  }

  @Test
  void distributeSellWithCap_capsAllocationExactlyAtMarketValue() {
    var result =
        TradeDistribution.distributeSellWithCap(
            TUV100,
            List.of(new BigDecimal("10000")),
            List.of(new BigDecimal("1")),
            new BigDecimal("10000"),
            new BigDecimal("5000"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("10000"));
  }

  @Test
  void distributeSellWithCap_leavesAllocationUncappedOneCentBelowMarketValue() {
    var result =
        TradeDistribution.distributeSellWithCap(
            TUV100,
            List.of(new BigDecimal("10000")),
            List.of(new BigDecimal("1")),
            new BigDecimal("9999.99"),
            new BigDecimal("5000"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("9999.99"));
  }

  @Test
  void distributeSellWithCap_redistributesOverflowToOtherPositionAfterOneIsFullyCapped() {
    var result =
        TradeDistribution.distributeSellWithCap(
            TUV100,
            List.of(new BigDecimal("3000"), new BigDecimal("50000")),
            List.of(new BigDecimal("1"), new BigDecimal("1")),
            new BigDecimal("8000"),
            new BigDecimal("100"));

    assertThat(result.get(0)).isEqualByComparingTo(new BigDecimal("3000"));
    assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("5000"));
  }
}
