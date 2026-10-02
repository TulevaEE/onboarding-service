package ee.tuleva.onboarding.investment.check.tracking;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.deadline.PublicHolidays;
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
