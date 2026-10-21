package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;

sealed interface BenchmarkModelCheck {

  record Measured(TrackingDifferenceResult result) implements BenchmarkModelCheck {}

  record NoHoldingMeasured(
      TulevaFund fund, LocalDate checkDate, List<String> holdingsWithoutBenchmarkPrices)
      implements BenchmarkModelCheck {

    String reason() {
      return "BENCHMARK_MODEL measured no holding for want of a benchmark price"
          + " (insert it, then backfill the date): holdings="
          + String.join(", ", holdingsWithoutBenchmarkPrices);
    }

    String describe() {
      return "fund=%s, checkDate=%s, %s".formatted(fund, checkDate, reason());
    }
  }

  record NothingToMeasure() implements BenchmarkModelCheck {}
}
