package ee.tuleva.onboarding.investment.kid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContinuousPercentileTest {

  @Test
  void interpolatesBetweenTheTwoMiddleValuesOfAnEvenCountAsPostgresPercentileContDoes() {
    assertThat(ContinuousPercentile.of(List.of(0.1, 0.4, 0.2, 0.3), 0.5))
        .isCloseTo(0.25, within(1e-15));
  }

  @Test
  void interpolatesAHighPercentileBetweenItsNeighbours() {
    assertThat(ContinuousPercentile.of(List.of(0.1, 0.4, 0.2, 0.3), 0.95))
        .isCloseTo(0.385, within(1e-15));
    assertThat(ContinuousPercentile.of(List.of(0.5, 0.1, 0.3), 0.99))
        .isCloseTo(0.496, within(1e-15));
  }

  @Test
  void theMedianOfAnOddCountIsTheMiddleValue() {
    assertThat(ContinuousPercentile.of(List.of(3.0, 1.0, 2.0), 0.5)).isEqualTo(2.0);
  }

  @Test
  void anyPercentileOfOneValueIsThatValue() {
    assertThat(ContinuousPercentile.of(List.of(0.7), 0.95)).isEqualTo(0.7);
  }
}
