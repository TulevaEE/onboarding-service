package ee.tuleva.onboarding.savings.fund.pricecheck;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

record BookingDays(LocalDate first, LocalDate runDay) {

  static final LocalDate FIRST_BOOKING_DAY_THE_PER_DEAL_CHECK_COVERS = LocalDate.of(2026, 11, 26);
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  static BookingDays sinceLastChecked(Optional<LocalDate> lastChecked, LocalDate runDay) {
    return new BookingDays(
        lastChecked.map(day -> day.plusDays(1)).orElse(FIRST_BOOKING_DAY_THE_PER_DEAL_CHECK_COVERS),
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
