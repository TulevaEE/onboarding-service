package ee.tuleva.onboarding.admin;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ChildAmlBackfillRequest(
    @ValidPersonalCode String requesterPersonalCode, @NotNull Boolean dryRun) {}
