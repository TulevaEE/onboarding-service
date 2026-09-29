package ee.tuleva.onboarding.banking.message;

import java.math.BigDecimal;
import java.time.Instant;

public record BookedBalance(BigDecimal amount, Instant asOf) {}
