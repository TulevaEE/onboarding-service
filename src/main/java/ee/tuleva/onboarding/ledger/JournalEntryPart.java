package ee.tuleva.onboarding.ledger;

import static java.time.temporal.ChronoUnit.MICROS;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public record JournalEntryPart(
    String sourceKey,
    String documentType,
    LocalDate date,
    List<JournalEntryLine> lines,
    Set<String> replacedSourceKeys) {

  private static final ZoneId BOOKING_ZONE = ZoneId.of("Europe/Tallinn");

  Instant transactionDate() {
    return endOfBookingDay(date);
  }

  static Instant endOfBookingDay(LocalDate date) {
    return date.atTime(LocalTime.MAX).atZone(BOOKING_ZONE).toInstant().truncatedTo(MICROS);
  }
}
