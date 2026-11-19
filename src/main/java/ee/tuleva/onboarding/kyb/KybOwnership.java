package ee.tuleva.onboarding.kyb;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record KybOwnership(
    @Nullable String code,
    OwnershipRole role,
    @Nullable LocalDate startDate,
    @Nullable LocalDate endDate) {}
