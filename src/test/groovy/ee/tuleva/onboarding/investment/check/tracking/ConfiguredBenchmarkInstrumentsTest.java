package ee.tuleva.onboarding.investment.check.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfiguredBenchmarkInstrumentsTest {

  @Test
  void publishesTheInstrumentsAFundLevelBenchmarkIsPricedFrom() {
    assertThat(new ConfiguredBenchmarkInstruments().benchmarkIsins())
        .containsExactlyInAnyOrder("LU0826455353", "LU0839970364");
  }
}
