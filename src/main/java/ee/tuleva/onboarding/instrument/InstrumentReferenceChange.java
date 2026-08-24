package ee.tuleva.onboarding.instrument;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record InstrumentReferenceChange(
    Long id,
    String isin,
    String operation,
    String changedBy,
    Instant changedAt,
    @Nullable String oldValues,
    @Nullable String newValues) {}
