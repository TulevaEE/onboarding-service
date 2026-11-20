package ee.tuleva.onboarding.banking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface CurrentDayReport {

  record Booked(BigDecimal balance, Instant fetchedAt, Set<UUID> pendingEntryReferences)
      implements CurrentDayReport {}

  record Missing() implements CurrentDayReport {}

  record NoBookedBalance() implements CurrentDayReport {}

  record Unreadable() implements CurrentDayReport {}
}
