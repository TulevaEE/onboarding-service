package ee.tuleva.onboarding.investment.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record IndexValuesSnapshot(
    Long id,
    Instant snapshotTime,
    String key,
    LocalDate date,
    BigDecimal value,
    String provider,
    Instant sourceUpdatedAt,
    Instant createdAt) {}
