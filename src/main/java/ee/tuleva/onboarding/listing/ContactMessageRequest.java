package ee.tuleva.onboarding.listing;

import jakarta.validation.constraints.NotBlank;

public record ContactMessageRequest(@NotBlank String message) {}
