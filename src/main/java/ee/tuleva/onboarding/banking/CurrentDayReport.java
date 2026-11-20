package ee.tuleva.onboarding.banking;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface CurrentDayReport {

  record Booked(BigDecimal balance, Instant fetchedAt) implements CurrentDayReport {}

  record Missing() implements CurrentDayReport {}

  record Unreadable() implements CurrentDayReport {}
}
