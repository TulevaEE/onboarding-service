package ee.tuleva.onboarding.auth.smartid;

import jakarta.validation.constraints.NotBlank;

public record SmartIdCallback(
    @NotBlank String value,
    @NotBlank String sessionSecretDigest,
    @NotBlank String userChallengeVerifier) {}
