package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

record OwnershipCheckRun(YearMonth month, List<Result> results, List<NotChecked> fundsNotChecked) {

  record Result(
      TulevaFund fund,
      LocalDate checkDate,
      List<OwnershipBreach> holdings,
      List<UnverifiedHolding> unverified,
      List<LeftOutHolding> leftOut) {

    BreachSeverity worstSeverity() {
      return holdings.stream().map(OwnershipBreach::severity).max(naturalOrder()).orElse(OK);
    }

    boolean coveredEveryHolding() {
      return !holdings.isEmpty() && unverified.isEmpty();
    }

    Optional<OwnershipBreach> largest() {
      return holdings.stream().max(comparing(OwnershipBreach::actualPercent));
    }
  }

  record NotChecked(TulevaFund fund, String reason) {}

  BreachSeverity worstSeverity() {
    return results.stream().map(Result::worstSeverity).max(naturalOrder()).orElse(OK);
  }

  boolean leftAFundWhollyUnchecked() {
    return (results.isEmpty() && fundsNotChecked.isEmpty())
        || !fundsNotChecked.isEmpty()
        || results.stream().anyMatch(result -> result.holdings().isEmpty());
  }

  boolean coveredEveryHolding() {
    return !leftAFundWhollyUnchecked() && results.stream().allMatch(Result::coveredEveryHolding);
  }
}
