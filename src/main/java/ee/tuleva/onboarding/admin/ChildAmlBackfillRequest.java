package ee.tuleva.onboarding.admin;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotNull;

public record ChildAmlBackfillRequest(
    @ValidPersonalCode String requesterPersonalCode, @NotNull Boolean dryRun) {}
