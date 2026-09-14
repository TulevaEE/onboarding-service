package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;

record LimitCheckRun(
    List<LimitCheckResult> results,
    List<TulevaFund> fundsNotChecked,
    List<UnfilledGap> unfilledGaps) {

  record UnfilledGap(
      TulevaFund fund, LocalDate checkDate, long daysUnfilled, LocalDate lastAttempt) {}

  LimitCheckRun(List<LimitCheckResult> results, List<TulevaFund> fundsNotChecked) {
    this(results, fundsNotChecked, List.of());
  }

  static LimitCheckRun of(List<LimitCheckResult> results) {
    return new LimitCheckRun(results, List.of(), List.of());
  }

  boolean isEmpty() {
    return results.isEmpty() && fundsNotChecked.isEmpty() && unfilledGaps.isEmpty();
  }

  boolean hasBreaches() {
    return results.stream().anyMatch(LimitCheckResult::hasBreaches);
  }
}
