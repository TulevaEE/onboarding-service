package ee.tuleva.onboarding.savings.fund.pricecheck;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

record BookingDays(LocalDate first, LocalDate runDay) {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  static BookingDays sinceLastChecked(
      Optional<LocalDate> lastChecked, LocalDate runDay, PublicHolidays publicHolidays) {
    return new BookingDays(
        lastChecked.map(day -> day.plusDays(1)).orElse(publicHolidays.previousWorkingDay(runDay)),
        runDay);
  }

  boolean isEmpty() {
    return !first.isBefore(runDay);
  }

  LocalDate last() {
    return runDay.minusDays(1);
  }

  Instant from() {
    return first.atStartOfDay(TALLINN).toInstant();
  }

  Instant to() {
    return runDay.atStartOfDay(TALLINN).toInstant();
  }

  String describe() {
    return first.equals(last()) ? first.toString() : first + "–" + last();
  }
}
