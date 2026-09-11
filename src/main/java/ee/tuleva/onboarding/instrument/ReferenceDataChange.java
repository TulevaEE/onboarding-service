package ee.tuleva.onboarding.instrument;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ReferenceDataChange(
    Long id,
    String tableName,
    String recordKey,
    String operation,
    String changedBy,
    Instant changedAt,
    @Nullable String oldValues,
    @Nullable String newValues) {}
