package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PastPerformancePropertiesTest {

  @Test
  void failsAtStartupWhenTheBenchmarksAreNotConfiguredRatherThanShowingEveryFundWithoutOne() {
    assertThatThrownBy(() -> new PastPerformanceProperties(null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aFundLeftOutOfTheBenchmarksHasNone() {
    var properties = new PastPerformanceProperties(Map.of(TUK75, "MSCI_ACWI"));

    assertThat(properties.benchmarkFor(TUK00)).isNull();
  }
}
