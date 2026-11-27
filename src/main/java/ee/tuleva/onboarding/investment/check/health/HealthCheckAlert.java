package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.jspecify.annotations.Nullable;

record HealthCheckAlert(
    long id,
    ReportProvider provider,
    LocalDate reportDate,
    String message,
    Instant foundAt,
    int attempts,
    @Nullable Instant lastAttemptAt) {

  private static final Duration POSTED_LATE_WHEN_MORE_THAN_THIS_AFTER_ITS_STATE_WAS_FOUND =
      Duration.ofMinutes(5);
  private static final DateTimeFormatter FOUND_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of(TIMEZONE));

  String messagePostedAt(Instant postedAt) {
    if (!foundAt
        .plus(POSTED_LATE_WHEN_MORE_THAN_THIS_AFTER_ITS_STATE_WAS_FOUND)
        .isBefore(postedAt)) {
      return message;
    }
    var firstLineEnd = message.indexOf('\n');
    var at = firstLineEnd < 0 ? message.length() : firstLineEnd;
    return message.substring(0, at)
        + " (found %s, posted late)".formatted(FOUND_TIME.format(foundAt))
        + message.substring(at);
  }

  boolean foundBefore(Instant instant) {
    return foundAt.isBefore(instant);
  }
}
