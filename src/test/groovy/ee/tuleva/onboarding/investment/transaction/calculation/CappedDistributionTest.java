package ee.tuleva.onboarding.investment.transaction.calculation;

import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CappedDistributionTest {

  @Test
  void distributeCapped_singlePositionCapBelowThresholdToleranceStaysAtZero() {
    var result =
        CappedDistribution.distributeCapped(
            List.of(new BigDecimal("1")),
            new BigDecimal("100"),
            new BigDecimal("100"),
            List.of(new BigDecimal("99.98")));

    assertThat(result[0]).isEqualByComparingTo(ZERO);
  }

  @Test
  void distributeCapped_singlePositionCapExactlyAtThresholdToleranceGetsCappedImmediately() {
    var result =
        CappedDistribution.distributeCapped(
            List.of(new BigDecimal("1")),
            new BigDecimal("100"),
            new BigDecimal("100"),
            List.of(new BigDecimal("99.99")));

    assertThat(result[0]).isEqualByComparingTo(new BigDecimal("99.99"));
  }

  @Test
  void distributeCapped_eliminatesBelowToleranceAllocationWithoutTriggeringCap() {
    var result =
        CappedDistribution.distributeCapped(
            List.of(new BigDecimal("1"), new BigDecimal("100")),
            new BigDecimal("101"),
            new BigDecimal("50"),
            List.of(new BigDecimal("100000"), new BigDecimal("100000")));

    assertThat(result[0]).isEqualByComparingTo(ZERO);
    assertThat(result[1]).isEqualByComparingTo(new BigDecimal("101.00"));
  }

  @Test
  void distributeCapped_clearsActiveSetAfterFullyFixingEveryone() {
    var result =
        CappedDistribution.distributeCapped(
            List.of(new BigDecimal("1"), new BigDecimal("1")),
            new BigDecimal("100"),
            ZERO,
            List.of(new BigDecimal("1000"), new BigDecimal("1000")));

    assertThat(result[0]).isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(result[1]).isEqualByComparingTo(new BigDecimal("50.00"));
  }
}
