package ee.tuleva.onboarding.party.admin;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record MinorGuardianLookupRequest(
    @ValidPersonalCode String requesterPersonalCode,
    @NotNull @Min(1) @Max(MinorGuardianLookupRequest.MAX_BATCH_SIZE) Integer batchSize) {

  static final int MAX_BATCH_SIZE = 1000;
}
