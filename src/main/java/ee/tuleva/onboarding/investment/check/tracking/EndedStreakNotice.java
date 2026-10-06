package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatPercent;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.check.tracking.ConsecutiveBreachTracker.ConsecutiveBreachInfo;

final class EndedStreakNotice {

  private EndedStreakNotice() {}

  static String format(TrackingDifferenceResult result) {
    var endedStreak = requireNonNull(result.streakBefore());
    return (endedStreak.uncheckedDaysSince() > 0
            ? afterUncheckedDays(result, endedStreak)
            : onTheDayItFallsDue(result, endedStreak))
        + EscalationSection.ofEndedStreak(endedStreak).describe()
        + navResidualBreachNote(endedStreak);
  }

  private static String onTheDayItFallsDue(
      TrackingDifferenceResult result, ConsecutiveBreachInfo endedStreak) {
    return ("\n🛑 [%s] %s %s: within limits (TD=%s%%), but the %d working days before it"
            + " breached. Sisekord 4 p 11.8 makes the notification due on this day, working day"
            + " %d: identify the cause and act on it.")
        .formatted(
            result.fund(),
            result.checkType(),
            result.checkDate(),
            formatPercent(result.trackingDifference()),
            endedStreak.count(),
            endedStreak.count() + 1);
  }

  private static String afterUncheckedDays(
      TrackingDifferenceResult result, ConsecutiveBreachInfo endedStreak) {
    return ("\n🛑 [%s] %s %s: within limits (TD=%s%%). The last check before it, %d working"
            + " days earlier, closed a %d-day breach streak, and no check ran in between."
            + " Sisekord 4 p 11.8 made the notification due on working day %d, which had no"
            + " check, so it is sent now, late: identify the cause and act on it.")
        .formatted(
            result.fund(),
            result.checkType(),
            result.checkDate(),
            formatPercent(result.trackingDifference()),
            endedStreak.uncheckedDaysSince() + 1,
            endedStreak.count(),
            endedStreak.count() + 1);
  }

  private static String navResidualBreachNote(ConsecutiveBreachInfo endedStreak) {
    return endedStreak.hadNavResidualBreach()
        ? "\n  The streak includes a NAV residual breach, which escalates whatever its TD."
        : "";
  }
}
