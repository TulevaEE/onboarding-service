package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;

record LimitCheckRun(List<LimitCheckResult> results, List<TulevaFund> fundsNotChecked) {

  static LimitCheckRun of(List<LimitCheckResult> results) {
    return new LimitCheckRun(results, List.of());
  }

  boolean isEmpty() {
    return results.isEmpty() && fundsNotChecked.isEmpty();
  }

  boolean hasBreaches() {
    return results.stream().anyMatch(LimitCheckResult::hasBreaches);
  }
}
