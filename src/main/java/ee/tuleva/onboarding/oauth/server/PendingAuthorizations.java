package ee.tuleva.onboarding.oauth.server;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.oauth2.core.endpoint.PkceParameterNames.CODE_CHALLENGE;
import static org.springframework.security.oauth2.core.endpoint.PkceParameterNames.CODE_CHALLENGE_METHOD;

import java.security.SecureRandom;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PendingAuthorizations {

  static final Duration LIFETIME = Duration.ofMinutes(10);

  private static final SecureRandom RANDOM = new SecureRandom();

  private final JdbcClient jdbcClient;
  private final Clock clock;

  Started start(OAuth2AuthorizationCodeRequestAuthenticationToken request) {
    var id = UUID.randomUUID();
    var browserBinding = randomSecret();
    jdbcClient
        .sql(
            """
            INSERT INTO oauth_pending_authorization (id, client_id, redirect_uri, state, scopes,
              code_challenge, code_challenge_method, browser_binding_hash, created_at)
            VALUES (:id, :clientId, :redirectUri, :state, :scopes, :codeChallenge,
              :codeChallengeMethod, :browserBindingHash, :createdAt)
            """)
        .param("id", id)
        .param("clientId", request.getClientId())
        .param("redirectUri", requireNonNull(request.getRedirectUri()))
        .param("state", request.getState(), Types.VARCHAR)
        .param("scopes", String.join(" ", request.getScopes()))
        .param("codeChallenge", request.getAdditionalParameters().get(CODE_CHALLENGE))
        .param("codeChallengeMethod", request.getAdditionalParameters().get(CODE_CHALLENGE_METHOD))
        .param("browserBindingHash", TokenHash.of(browserBinding))
        .param("createdAt", Timestamps.of(clock.instant()))
        .update();
    return new Started(id, browserBinding);
  }

  Optional<PendingAuthorization> findOpen(UUID id, String browserBinding) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM oauth_pending_authorization
            WHERE id = :id AND browser_binding_hash = :browserBindingHash
              AND consumed_at IS NULL AND created_at > :openSince
            """)
        .param("id", id)
        .param("browserBindingHash", TokenHash.of(browserBinding))
        .param("openSince", Timestamps.of(clock.instant().minus(LIFETIME)))
        .query(
            (resultSet, rowNum) ->
                new PendingAuthorization(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("client_id"),
                    resultSet.getString("redirect_uri"),
                    resultSet.getString("state"),
                    Arrays.stream(resultSet.getString("scopes").split(" "))
                        .collect(Collectors.toUnmodifiableSet()),
                    resultSet.getString("code_challenge"),
                    resultSet.getString("code_challenge_method"),
                    requireNonNull(Timestamps.read(resultSet, "created_at"))))
        .optional();
  }

  boolean consume(UUID id) {
    return jdbcClient
            .sql(
                """
                UPDATE oauth_pending_authorization SET consumed_at = :now
                WHERE id = :id AND consumed_at IS NULL
                """)
            .param("now", Timestamps.of(clock.instant()))
            .param("id", id)
            .update()
        == 1;
  }

  private static String randomSecret() {
    var bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  record Started(UUID id, String browserBinding) {}
}
