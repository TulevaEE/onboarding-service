package ee.tuleva.onboarding.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessAndRefreshToken(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken) {}
