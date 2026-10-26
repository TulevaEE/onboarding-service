package ee.tuleva.onboarding.investment.kid.performance;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment.past-performance")
record PastPerformanceProperties(Map<TulevaFund, String> benchmarks) {

  PastPerformanceProperties {
    if (benchmarks == null) {
      throw new IllegalStateException(
          "Past-performance benchmarks not configured: prefix=investment.past-performance");
    }
    benchmarks = Map.copyOf(benchmarks);
  }

  @Nullable String benchmarkFor(TulevaFund fund) {
    return benchmarks.get(fund);
  }
}
