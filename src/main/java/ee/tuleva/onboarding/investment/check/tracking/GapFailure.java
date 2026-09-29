package ee.tuleva.onboarding.investment.check.tracking;

import static java.util.stream.Collectors.joining;

import java.time.LocalDate;
import java.util.List;

record GapFailure(LocalDate checkDate, String reason, long daysUnfilled, LocalDate lastAttempt) {

  private static final int FRESH_GAP_DAYS = 5;
  private static final String STANDING_GAP =
      "%s [standing gap: unfilled for %d days, last attempt %s — the missing price has to be"
          + " inserted by hand, nothing backfills it]";

  static String report(List<GapFailure> failures) {
    return failures.stream()
        .map(GapFailure::describe)
        .collect(joining("\n", "Incomplete security price data:\n", ""));
  }

  boolean isStanding() {
    return daysUnfilled > FRESH_GAP_DAYS;
  }

  String describe() {
    var line = "checkDate=%s, %s".formatted(checkDate, reason);
    return isStanding() ? STANDING_GAP.formatted(line, daysUnfilled, lastAttempt) : line;
  }
}
