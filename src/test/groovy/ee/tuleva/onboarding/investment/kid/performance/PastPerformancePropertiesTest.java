package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

              assertThat(properties)
                  .isEqualTo(new PastPerformanceProperties(Map.of(TUK75, "MSCI_ACWI"), Map.of()));
              assertThat(properties.benchmarkFor(TUK00)).isNull();
            });
  }

  @Test
  void bindsAFundsEquityCapToItsBenchmark() {
    var equityCap =
        new EquityCap("EURO_AGGREGATE_BOND", new BigDecimal("0.75"), LocalDate.of(2019, 9, 2));
    contextRunner
        .withPropertyValues(
            "investment.past-performance.benchmarks.TUK75=MSCI_ACWI",
            "investment.past-performance.benchmarks.TKF100=MSCI_ACWI",
            "investment.past-performance.equity-caps.TUK75.bond-key=EURO_AGGREGATE_BOND",
            "investment.past-performance.equity-caps.TUK75.equity-weight=0.75",
            "investment.past-performance.equity-caps.TUK75.lifted-on=2019-09-02")
        .run(
            context -> {
              var properties = context.getBean(PastPerformanceProperties.class);

              assertThat(properties.benchmarkFor(TUK75))
                  .isEqualTo(new Benchmark("MSCI_ACWI", equityCap));
              assertThat(properties.benchmarkFor(TKF100))
                  .isEqualTo(new Benchmark("MSCI_ACWI", null));
            });
  }

  @ParameterizedTest
  @MethodSource("equityCapsThatCannotBeApplied")
  void failsAtStartupOnAnEquityCapThatCannotBeApplied(String[] equityCap) {
    contextRunner
        .withPropertyValues("investment.past-performance.benchmarks.TUK75=MSCI_ACWI")
        .withPropertyValues(equityCap)
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class));
  }

  static Stream<Arguments> equityCapsThatCannotBeApplied() {
    return Stream.of(
        arguments(
            (Object)
                new String[] {"investment.past-performance.equity-caps.TUK75.equity-weight=0.75"}),
        arguments(
            (Object)
                new String[] {
                  "investment.past-performance.equity-caps.TUK00.bond-key=EURO_AGGREGATE_BOND",
                  "investment.past-performance.equity-caps.TUK00.equity-weight=0.75",
                  "investment.past-performance.equity-caps.TUK00.lifted-on=2019-09-02"
                }),
        arguments(
            (Object)
                new String[] {
                  "investment.past-performance.equity-caps.TUK75.bond-key=EURO_AGGREGATE_BOND",
                  "investment.past-performance.equity-caps.TUK75.equity-weight=75",
                  "investment.past-performance.equity-caps.TUK75.lifted-on=2019-09-02"
                }));
  }
}
