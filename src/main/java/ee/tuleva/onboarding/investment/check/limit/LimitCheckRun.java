package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;

/**
 * What one pass of the limit check covered, which is not the same as what it found. A fund with no
 * position data is not checked at all, and a run that reports only its results cannot tell "within
 * limits" apart from "never looked".
 */
record LimitCheckRun(List<LimitCheckResult> results, List<TulevaFund> notChecked) {

  static LimitCheckRun of(List<LimitCheckResult> results) {
    return new LimitCheckRun(results, List.of());
  }

  boolean isEmpty() {
    return results.isEmpty() && notChecked.isEmpty();
  }

  boolean hasBreaches() {
    return results.stream().anyMatch(LimitCheckResult::hasBreaches);
  }
}
