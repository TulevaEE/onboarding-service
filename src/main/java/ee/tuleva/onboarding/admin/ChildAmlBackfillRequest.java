package ee.tuleva.onboarding.admin;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;

public record ChildAmlBackfillRequest(
    @ValidPersonalCode String requesterPersonalCode, boolean dryRun) {}
