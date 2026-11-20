package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import java.math.BigDecimal;
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

  boolean paysFor(TransactionOrder order, String reference) {
    return booking(reference).filter(booking -> fits(order, booking)).isPresent();
  }

  static boolean fits(TransactionOrder order, TradeSettlementBooking booking) {
    BigDecimal cash = booking.cashAmount();
    return cash != null
        && order.getFund() == booking.fund()
        && order.getInstrumentIsin().equals(booking.isin())
        && cashLeftTheFund(cash) == (order.getTransactionType() == BUY);
  }

  private static boolean cashLeftTheFund(BigDecimal cash) {
    return cash.signum() < 0;
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
