package ee.tuleva.onboarding.investment.check.tracking;

import java.util.List;

sealed interface FundCheck {

  default List<TrackingDifferenceResult> results() {
    return List.of();
  }

  record Checked(List<TrackingDifferenceResult> results) implements FundCheck {}

  record NotCheckable(String reason) implements FundCheck {}

  record NeverCheckable() implements FundCheck {}
}
