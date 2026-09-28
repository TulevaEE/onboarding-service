package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarContribution(LocalDate bookingDate, BigDecimal amount) {}
