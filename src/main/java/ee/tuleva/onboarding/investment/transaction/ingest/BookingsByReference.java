package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class BookingsByReference {

  private final Map<String, TradeSettlementBooking> byReference = new HashMap<>();

  BookingsByReference(
      List<TradeSettlementBooking> bookings, Map<UUID, String> referencesOfOlderBookings) {
    for (TradeSettlementBooking booking : bookings) {
      String reference = referenceOf(booking, referencesOfOlderBookings);
      if (reference != null) {
        byReference.putIfAbsent(reference, booking);
      }
    }
  }

  Optional<TradeSettlementBooking> booking(@Nullable String reference) {
    return reference == null ? Optional.empty() : Optional.ofNullable(byReference.get(reference));
  }

  private static @Nullable String referenceOf(
      TradeSettlementBooking booking, Map<UUID, String> referencesOfOlderBookings) {
    if (booking.brokerReference() != null) {
      return booking.brokerReference();
    }
    UUID externalReference = booking.externalReference();
    return externalReference == null ? null : referencesOfOlderBookings.get(externalReference);
  }
}
