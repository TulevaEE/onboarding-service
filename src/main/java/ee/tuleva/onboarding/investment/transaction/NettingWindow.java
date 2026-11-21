package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record NettingWindow(
    TulevaFund fund,
    LocalDate reportDate,
    LocalDate today,
    Set<UUID> pendingEntryReferences,
    Set<UUID> bookedTodayEntryReferences)
    implements CashHorizon {

  @Override
  public LocalDate lastBookingDay() {
    return today;
  }

  @Override
  public boolean holds(@Nullable UUID entryReference, LocalDate bookingDate) {
    if (entryReference != null && pendingEntryReferences.contains(entryReference)) {
      return false;
    }
    return bookingDate.isBefore(today)
        || entryReference != null && bookedTodayEntryReferences.contains(entryReference);
  }
}
