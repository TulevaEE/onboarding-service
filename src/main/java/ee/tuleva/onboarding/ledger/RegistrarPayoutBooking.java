package ee.tuleva.onboarding.ledger;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RegistrarPayoutBooking(
    LocalDate bookingDate, RegistrarPayoutReason reason, @Nullable UUID externalReference) {}
