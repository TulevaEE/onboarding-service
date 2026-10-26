package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.GapCause.MISSING_PRICE;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.BenchmarkModelCheck.NoHoldingMeasured;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;

record GapFailure(
    LocalDate checkDate, String reason, GapCause cause, long daysUnfilled, LocalDate lastAttempt) {

  private static final int FRESH_GAP_DAYS = 5;
  private static final String STANDING_GAP =
      "%s [standing gap: unfilled for %d days, last attempt %s — %s]";

  static GapFailure inWindow(
      TulevaFund fund,
      LocalDate checkDate,
      GapWindow window,
      String reason,
      GapCause cause,
      PublicHolidays publicHolidays) {
    return new GapFailure(
        checkDate,
        "fund=%s, %s".formatted(fund, reason),
        cause,
        DAYS.between(checkDate, window.today()),
        lastAttemptDate(checkDate, window.lookbackDays(), publicHolidays));
  }

  static List<GapFailure> notMeasured(
      List<NoHoldingMeasured> checksNotMeasured, GapWindow window, PublicHolidays publicHolidays) {
    return checksNotMeasured.stream()
        .map(
            notMeasured ->
                inWindow(
                    notMeasured.fund(),
                    notMeasured.checkDate(),
                    window,
                    notMeasured.reason(),
                    MISSING_PRICE,
                    publicHolidays))
        .toList();
  }

  private static LocalDate lastAttemptDate(
      LocalDate checkDate, int lookbackDays, PublicHolidays publicHolidays) {
    var lastDayInWindow = checkDate.plusDays(lookbackDays);
    return publicHolidays.isWorkingDay(lastDayInWindow)
        ? lastDayInWindow
        : publicHolidays.previousWorkingDay(lastDayInWindow);
  }

  static String report(List<GapFailure> failures) {
    return failures.stream()
        .map(GapFailure::describe)
        .collect(joining("\n", "Dates the TD gap fill could not check:\n", ""));
  }

  boolean isStanding() {
    return daysUnfilled > FRESH_GAP_DAYS;
  }

  String describe() {
    var line = "checkDate=%s, %s".formatted(checkDate, reason);
    return isStanding()
        ? STANDING_GAP.formatted(line, daysUnfilled, lastAttempt, cause.whatFillsIt())
        : line;
  }
}
