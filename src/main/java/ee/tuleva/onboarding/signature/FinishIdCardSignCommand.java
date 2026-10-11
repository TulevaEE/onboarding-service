package ee.tuleva.onboarding.signature;

import jakarta.validation.constraints.NotBlank;

public record FinishIdCardSignCommand(@NotBlank String signature) {}
