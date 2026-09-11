package ee.tuleva.onboarding.signature;

import jakarta.validation.constraints.NotBlank;

public record StartIdCardSignCommand(@NotBlank String clientCertificate) {}
