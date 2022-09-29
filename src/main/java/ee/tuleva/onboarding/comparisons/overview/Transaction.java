package ee.tuleva.onboarding.comparisons.overview;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public record Transaction(BigDecimal amount, Instant time) {

  public LocalDate date() {
    return time.atZone(ZoneOffset.UTC).toLocalDate();
  }
}
