package ee.tuleva.onboarding.ariregister;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record BeneficialOwner(
    @Nullable String firstName,
    @Nullable String lastName,
    @Nullable String personalCode,
    @Nullable String controlMethod) {}
