package ee.tuleva.onboarding.ariregister;

import jakarta.annotation.Nullable;
import java.time.LocalDate;

public record RepresentationRight(
    @Nullable String type,
    @Nullable String typeText,
    @Nullable String content,
    @Nullable LocalDate startDate,
    @Nullable LocalDate endDate,
    @Nullable Long entryId) {}
