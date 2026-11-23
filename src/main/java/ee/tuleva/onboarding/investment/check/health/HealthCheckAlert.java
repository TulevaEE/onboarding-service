package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

record HealthCheckAlert(
    long id, ReportProvider provider, LocalDate reportDate, String message, Instant foundAt) {

  private static final DateTimeFormatter FOUND_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of(TIMEZONE));

  String lateMessage() {
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
