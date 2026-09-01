package ee.tuleva.onboarding.party.admin;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ChildAmlBackfillRequest(
    @ValidPersonalCode String requesterPersonalCode, @NotNull Boolean dryRun) {}
