package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.oauth.server.Grants.RevocationReason.REFRESH_TOKEN_REUSED;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

class GrantAuthorizationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-08T09:00:00Z");
  private static final UUID GRANT_ID = UUID.fromString("7d4c4d5e-35a4-4d1b-9a8e-2f0b6f7c1a11");

  private final Grants grants = mock(Grants.class);
  private final GrantAuthorizationService service =
      new GrantAuthorizationService(
          grants,
          mock(RegisteredClientRepository.class),
          AuthorizationServerSettings.builder().issuer("http://localhost:9000").build());

  private final RegisteredClient client =
      RegisteredClient.withId("portfellow")
          .clientId("portfellow")
          .authorizationGrantType(AUTHORIZATION_CODE)
          .redirectUri("https://app.example/callback")
          .build();

  @Test
  void aRefreshThatLosesARaceForTheSameRefreshTokenEndsTheGrant() {
    given(grants.byId(GRANT_ID)).willReturn(Optional.of(liveGrant()));
    given(
            grants.rotateRefreshToken(
                eq(GRANT_ID), any(), any(), any(), eq("hash-of-the-presented-refresh-token")))
        .willReturn(false);
    var refreshedByTheLoser =
        OAuth2Authorization.withRegisteredClient(client)
            .id(GRANT_ID.toString())
            .principalName("subject")
            .authorizationGrantType(AUTHORIZATION_CODE)
            .refreshToken(new OAuth2RefreshToken("new-refresh-token", NOW, NOW.plus(30, DAYS)))
            .attribute(
                GrantAuthorizationService.LOADED_REFRESH_TOKEN_HASH,
                "hash-of-the-presented-refresh-token")
            .build();

    assertThatThrownBy(() -> service.save(refreshedByTheLoser))
        .isInstanceOf(OAuth2AuthenticationException.class);
    then(grants).should().revoke(GRANT_ID, REFRESH_TOKEN_REUSED);
  }

  private static GrantRow liveGrant() {
    return new GrantRow(
        GRANT_ID,
        "portfellow",
        UUID.fromString("0b9a7c55-7f4e-4a59-8a7b-3c2d1e0f9a88"),
        "38812121215",
        Set.of("balances:read"),
        "https://app.example/callback",
        "client-state",
        "challenge",
        "S256",
        NOW.plus(170, DAYS),
        NOW.minus(1, DAYS),
        null,
        null,
        null,
        true,
        "hash-of-the-access-token",
        NOW.minus(1, DAYS),
        NOW.minus(1, DAYS).plusSeconds(600),
        "hash-of-the-presented-refresh-token",
        NOW.minus(1, DAYS),
        NOW.plus(29, DAYS),
        null);
  }
}
