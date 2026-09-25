package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static java.util.stream.Collectors.toUnmodifiableMap;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

record GapFillRun(
    List<TrackingDifferenceResult> results,
    List<GapFailure> failures,
    Map<TulevaFund, List<LocalDate>> staleCheckDates) {

  static final GapFillRun NOTHING_TO_FILL = new GapFillRun(List.of(), List.of(), Map.of());

  static GapFillRun forFund(
      TulevaFund fund,
      List<TrackingDifferenceResult> results,
      List<GapFailure> failures,
      List<LocalDate> staleCheckDates) {
    return new GapFillRun(
        List.copyOf(results),
        List.copyOf(failures),
        staleCheckDates.isEmpty() ? Map.of() : Map.of(fund, List.copyOf(staleCheckDates)));
  }

  Map<TulevaFund, List<LocalDate>> recheckedStaleDates() {
    return staleCheckDates.entrySet().stream()
        .map(entry -> Map.entry(entry.getKey(), rechecked(entry.getKey(), entry.getValue())))
        .filter(entry -> !entry.getValue().isEmpty())
        .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  List<LocalDate> staleDatesNotRechecked(TulevaFund fund) {
    return staleCheckDates.getOrDefault(fund, List.of()).stream()
        .filter(checkDate -> !wasRechecked(fund, checkDate))
        .toList();
  }

  private List<LocalDate> rechecked(TulevaFund fund, List<LocalDate> checkDates) {
    return checkDates.stream().filter(checkDate -> wasRechecked(fund, checkDate)).toList();
  }

  private boolean wasRechecked(TulevaFund fund, LocalDate checkDate) {
    return results.stream()
        .anyMatch(
            result ->
                result.checkType() == MODEL_PORTFOLIO
                    && result.fund() == fund
                    && result.checkDate().equals(checkDate));
  }

  GapFillRun and(GapFillRun other) {
    return new GapFillRun(
        Stream.concat(results.stream(), other.results.stream()).toList(),
        Stream.concat(failures.stream(), other.failures.stream()).toList(),
        Stream.concat(
                staleCheckDates.entrySet().stream(), other.staleCheckDates.entrySet().stream())
            .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
  }
}
