package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarPayout(
    LocalDate bookingDate, BigDecimal amount, RegistrarPayoutReason reason) {}
