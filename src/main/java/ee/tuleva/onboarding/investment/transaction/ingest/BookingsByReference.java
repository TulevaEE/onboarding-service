package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Slf4j
final class BookingsByReference {

  private final Map<String, List<TradeSettlementBooking>> byReference = new HashMap<>();

  BookingsByReference(
      List<TradeSettlementBooking> bookings, Map<UUID, String> referencesOfOlderBookings) {
    for (TradeSettlementBooking booking : bookings) {
      String reference = referenceOf(booking, referencesOfOlderBookings);
      if (reference != null) {
        byReference.computeIfAbsent(reference, key -> new ArrayList<>()).add(booking);
      }
    }
    byReference.forEach(
        (reference, booked) -> {
          if (booked.size() > 1) {
            log.warn(
                "Bank booked one SEB reference more than once, none settles an order:"
                    + " brokerReference={}, bookings={}",
                reference,
                booked.size());
          }
        });
  }

  Optional<TradeSettlementBooking> booking(@Nullable String reference) {
    List<TradeSettlementBooking> booked =
        reference == null ? List.of() : byReference.getOrDefault(reference, List.of());
    return booked.size() == 1 ? Optional.of(booked.getFirst()) : Optional.empty();
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
