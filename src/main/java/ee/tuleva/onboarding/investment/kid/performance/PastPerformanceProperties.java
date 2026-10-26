package ee.tuleva.onboarding.investment.kid.performance;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment.past-performance")
record PastPerformanceProperties(
    Map<TulevaFund, String> benchmarks, Map<TulevaFund, EquityCap> equityCaps) {

  PastPerformanceProperties {
    if (benchmarks == null) {
      throw new IllegalStateException(
          "Past-performance benchmarks not configured: prefix=investment.past-performance");
    }
    benchmarks = Map.copyOf(benchmarks);
    equityCaps = equityCaps == null ? Map.of() : Map.copyOf(equityCaps);
    if (!benchmarks.keySet().containsAll(equityCaps.keySet())) {
      throw new IllegalStateException(
          "Equity cap for a fund without a benchmark: cappedFunds=%s, benchmarkedFunds=%s"
              .formatted(equityCaps.keySet(), benchmarks.keySet()));
    }
  }

  @Nullable Benchmark benchmarkFor(TulevaFund fund) {
    var key = benchmarks.get(fund);
    return key == null ? null : new Benchmark(key, equityCaps.get(fund));
  }
}
