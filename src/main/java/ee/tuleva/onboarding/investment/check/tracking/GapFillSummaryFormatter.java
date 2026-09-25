package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatPercent;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class GapFillSummaryFormatter {

  private static final Comparator<TrackingDifferenceResult> BY_DATE_FUND_AND_CHECK_TYPE =
      Comparator.comparing(TrackingDifferenceResult::checkDate)
          .thenComparing(result -> result.fund().getCode())
          .thenComparing(result -> result.checkType().name());

  private GapFillSummaryFormatter() {}

  static String format(
      List<TrackingDifferenceResult> alertableResults,
      Map<TulevaFund, List<LocalDate>> recheckedStaleDates) {
    var dates =
        alertableResults.stream()
            .map(TrackingDifferenceResult::checkDate)
            .distinct()
            .sorted()
            .toList();
    var message =
        new StringBuilder(
            ("🕗 TD GAP FILL: %d past check dates rewritten, %s to %s — these are earlier days,"
                    + " not today's check")
                .formatted(dates.size(), dates.getFirst(), dates.getLast()));
    if (!recheckedStaleDates.isEmpty()) {
      message.append(formatRecheckedStaleDates(recheckedStaleDates));
    }
    var breaches =
        alertableResults.stream()
            .filter(TrackingDifferenceResult::hasAnyBreach)
            .sorted(BY_DATE_FUND_AND_CHECK_TYPE)
            .toList();
    if (breaches.isEmpty()) {
      message.append("\n  No breach on any of them.");
    } else {
      breaches.forEach(result -> message.append(formatBreach(result)));
    }
    return message.toString();
  }

  private static String formatRecheckedStaleDates(Map<TulevaFund, List<LocalDate>> rechecked) {
    return rechecked.entrySet().stream()
        .map(fundDates -> formatFundDates(fundDates.getKey(), fundDates.getValue()))
        .sorted()
        .collect(joining("; ", "\n  Rechecked because the NAV changed after the check ran: ", ""));
  }

  private static String formatFundDates(TulevaFund fund, List<LocalDate> checkDates) {
    return checkDates.stream()
        .sorted()
        .map(LocalDate::toString)
        .collect(joining(", ", fund.getCode() + " ", ""));
  }

  private static String formatBreach(TrackingDifferenceResult result) {
    return "\n  🛑 %s %s %s: TD=%s%%, %d consecutive days"
        .formatted(
            result.checkDate(),
            result.fund().getCode(),
            result.checkType(),
            formatPercent(result.trackingDifference()),
            result.consecutiveBreachDays());
  }
}
