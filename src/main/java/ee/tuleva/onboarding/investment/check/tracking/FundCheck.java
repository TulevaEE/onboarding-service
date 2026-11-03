package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.investment.check.tracking.BenchmarkModelCheck.NoHoldingMeasured;
import java.util.List;

sealed interface FundCheck {

  default List<TrackingDifferenceResult> results() {
    return List.of();
  }

  default List<NoHoldingMeasured> checksNotMeasured() {
    return List.of();
  }

  record Checked(List<TrackingDifferenceResult> results, List<NoHoldingMeasured> checksNotMeasured)
      implements FundCheck {}

  record NotCheckable(String reason) implements FundCheck {}

  record NeverCheckable() implements FundCheck {}

  record NavNotDueYet() implements FundCheck {}
}
