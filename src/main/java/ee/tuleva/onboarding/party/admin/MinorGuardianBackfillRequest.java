package ee.tuleva.onboarding.party.admin;

import static ee.tuleva.onboarding.party.admin.MinorGuardianLookupRequest.MAX_BATCH_SIZE;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record MinorGuardianBackfillRequest(
    @NotNull @Min(1) @Max(MAX_BATCH_SIZE) Integer batchSize) {}
