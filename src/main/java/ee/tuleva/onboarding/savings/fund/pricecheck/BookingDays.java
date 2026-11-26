package ee.tuleva.onboarding.savings.fund.pricecheck;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

record BookingDays(LocalDate first, LocalDate runDay) {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  static BookingDays before(LocalDate runDay, PublicHolidays publicHolidays) {
    return new BookingDays(publicHolidays.previousWorkingDay(runDay), runDay);
  }

  Instant from() {
    return first.atStartOfDay(TALLINN).toInstant();
  }

  Instant to() {
    return runDay.atStartOfDay(TALLINN).toInstant();
  }

  String describe() {
    LocalDate last = runDay.minusDays(1);
    return first.equals(last) ? first.toString() : first + "–" + last;
  }
}
