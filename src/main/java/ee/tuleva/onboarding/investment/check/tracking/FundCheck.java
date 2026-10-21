package ee.tuleva.onboarding.investment.check.tracking;

import java.util.List;

sealed interface FundCheck {

  default List<TrackingDifferenceResult> results() {
    return List.of();
  }

  default List<String> checksNotMeasured() {
    return List.of();
  }

  record Checked(List<TrackingDifferenceResult> results, List<String> checksNotMeasured)
      implements FundCheck {}

  record NotCheckable(String reason) implements FundCheck {}

  record NeverCheckable() implements FundCheck {}

  record NavNotDueYet() implements FundCheck {}
}
