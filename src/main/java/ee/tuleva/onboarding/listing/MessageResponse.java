package ee.tuleva.onboarding.listing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MessageResponse(@NotNull Long id, @NotBlank String status) {}
