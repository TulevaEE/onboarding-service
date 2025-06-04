package ee.tuleva.onboarding.listing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContactMessageRequest(
    @NotBlank String message, @NotNull ListingContactPreference contactPreference) {}
