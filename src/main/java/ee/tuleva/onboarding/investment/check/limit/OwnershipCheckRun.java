package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record OwnershipCheckRun(YearMonth month, List<Result> results, List<NotChecked> fundsNotChecked) {

  record Result(
      TulevaFund fund,
      LocalDate checkDate,
      List<OwnershipBreach> holdings,
      List<UnverifiedHolding> unverified,
      List<LeftOutHolding> leftOut,
      List<StaleFundSize> staleSizes,
      @Nullable String sizesNotComparedWithTheMonthBefore) {

    Result(
        TulevaFund fund,
        LocalDate checkDate,
        List<OwnershipBreach> holdings,
        List<UnverifiedHolding> unverified,
        List<LeftOutHolding> leftOut,
        List<StaleFundSize> staleSizes) {
      this(fund, checkDate, holdings, unverified, leftOut, staleSizes, null);
    }

    Result withStaleSizes(List<StaleFundSize> unchangedSizes) {
      return new Result(fund, checkDate, holdings, unverified, leftOut, unchangedSizes);
    }

    Result withSizesNotComparedBecause(String reason) {
      return new Result(fund, checkDate, holdings, unverified, leftOut, List.of(), reason);
    }

    BreachSeverity worstSeverity() {
      return holdings.stream().map(OwnershipBreach::severity).max(naturalOrder()).orElse(OK);
    }

    boolean coveredEveryHolding() {
      return unverified.isEmpty() && !(holdings.isEmpty() && leftOut.isEmpty());
    }

    boolean everyHoldingWasLeftOutByDesign() {
      return holdings.isEmpty() && coveredEveryHolding();
    }

    boolean measuredNothing() {
      return holdings.isEmpty() && !coveredEveryHolding();
    }

    boolean sizesWereComparedWithTheMonthBefore() {
      return sizesNotComparedWithTheMonthBefore == null;
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
        || results.stream().anyMatch(Result::measuredNothing);
  }

  boolean hasStaleFundSizes() {
    return results.stream().anyMatch(result -> !result.staleSizes().isEmpty());
  }

  boolean leftFundSizesUncompared() {
    return results.stream().anyMatch(result -> !result.sizesWereComparedWithTheMonthBefore());
  }

  boolean coveredEveryHolding() {
    return !leftAFundWhollyUnchecked() && results.stream().allMatch(Result::coveredEveryHolding);
  }
}
