package ee.tuleva.onboarding.investment.check.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BenchmarkModelTrackingDifferenceTest {

  private static final BigDecimal LIMIT = new BigDecimal("0.001");

  @Test
  void breachesLimit_whenAbsoluteTrackingDifferenceReachesLimit() {
    assertThat(trackingDifference("0.001073").breachesLimit()).isTrue();
    assertThat(trackingDifference("-0.001073").breachesLimit()).isTrue();
    assertThat(trackingDifference("0.001").breachesLimit()).isTrue();
  }

  @Test
  void doesNotBreachLimit_whenAbsoluteTrackingDifferenceBelowLimit() {
    assertThat(trackingDifference("0.000751").breachesLimit()).isFalse();
    assertThat(trackingDifference("-0.000999").breachesLimit()).isFalse();
  }

  private BenchmarkModelTrackingDifference trackingDifference(String value) {
    return new BenchmarkModelTrackingDifference(new BigDecimal(value), LIMIT);
  }
}
