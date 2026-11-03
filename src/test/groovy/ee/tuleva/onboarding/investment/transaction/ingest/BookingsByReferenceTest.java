package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingsByReferenceTest {

  private static final UUID OLDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID NEWER = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void aBookingCarryingItsSebReference_isFoundByIt() {
    TradeSettlementBooking booking = booking(NEWER, "DLA0553690");

    assertThat(new BookingsByReference(List.of(booking), Map.of()).booking("DLA0553690"))
        .contains(booking);
  }

  @Test
  void anOlderBookingWithoutOne_isFoundByTheReferenceReadFromItsStatement() {
    TradeSettlementBooking booking = booking(OLDER, null);

    assertThat(
            new BookingsByReference(List.of(booking), Map.of(OLDER, "DLA0553685"))
                .booking("DLA0553685"))
        .contains(booking);
  }

  @Test
  void aBookingWithNoReferenceAnywhere_andAMissingReference_findNothing() {
    BookingsByReference bookings = new BookingsByReference(List.of(booking(OLDER, null)), Map.of());

    assertThat(bookings.booking("DLA0553685")).isEmpty();
    assertThat(bookings.booking(null)).isEmpty();
  }

  private static TradeSettlementBooking booking(UUID externalReference, String brokerReference) {
    return new TradeSettlementBooking(
        externalReference,
        brokerReference,
        "LU1291102447",
        LocalDate.of(2026, 2, 12),
        new BigDecimal("-209025.86"));
  }
}
