package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PastPerformancePropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PastPerformanceConfiguration.class);

  @Test
  void failsAtStartupWhenTheBenchmarksAreNotConfiguredRatherThanShowingEveryFundWithoutOne() {
    contextRunner.run(
        context ->
            assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(IllegalStateException.class));
  }

  @Test
  void startsWithTheConfiguredBenchmarksAndAFundLeftOutOfThemHasNone() {
    contextRunner
        .withPropertyValues("investment.past-performance.benchmarks.TUK75=MSCI_ACWI")
        .run(
            context -> {
              var properties = context.getBean(PastPerformanceProperties.class);

              assertThat(properties.benchmarkFor(TUK75)).isEqualTo("MSCI_ACWI");
              assertThat(properties.benchmarkFor(TUK00)).isNull();
            });
  }
}
