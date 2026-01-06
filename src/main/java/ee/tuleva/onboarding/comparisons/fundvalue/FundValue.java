package ee.tuleva.onboarding.comparisons.fundvalue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FundValue(
    String key, LocalDate date, BigDecimal value, String provider, Instant updatedAt) {}
