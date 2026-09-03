package ee.tuleva.onboarding.conversion;

import java.math.BigDecimal;
import java.time.Instant;

public record ConversionCashFlow(
    int pillar,
    BigDecimal amount,
    Instant time,
    boolean cashContribution,
    boolean contribution,
    boolean subtraction) {}
