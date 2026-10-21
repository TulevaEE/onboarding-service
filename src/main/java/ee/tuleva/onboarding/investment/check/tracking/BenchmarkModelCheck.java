package ee.tuleva.onboarding.investment.check.tracking;

import java.util.List;

sealed interface BenchmarkModelCheck {

  record Measured(TrackingDifferenceResult result) implements BenchmarkModelCheck {}

  record NoHoldingMeasured(List<String> holdingsWithoutBenchmarkPrices)
      implements BenchmarkModelCheck {

    String reason() {
      return "BENCHMARK_MODEL measured no holding for want of a benchmark price"
          + " (insert it, then backfill the date): holdings="
          + String.join(", ", holdingsWithoutBenchmarkPrices);
    }
  }

  record NothingToMeasure() implements BenchmarkModelCheck {}
}
