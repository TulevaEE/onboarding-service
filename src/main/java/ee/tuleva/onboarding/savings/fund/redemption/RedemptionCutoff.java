package ee.tuleva.onboarding.savings.fund.redemption;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class RedemptionCutoff {

  static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0, 0);
  static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  static Instant cutoffInstant(LocalDate date) {
    return ZonedDateTime.of(date, CUTOFF_TIME, TALLINN).toInstant();
  }

  private RedemptionCutoff() {}
}
