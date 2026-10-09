package ee.tuleva.onboarding.oauth.server;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN;
import static org.springframework.security.oauth2.core.ClientAuthenticationMethod.PRIVATE_KEY_JWT;

import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
class ConfiguredClients implements RegisteredClientRepository {

  static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(10);
  static final Duration AUTHORIZATION_CODE_LIFETIME = Duration.ofMinutes(1);

  private final Map<String, RegisteredClient> clients;
  private final ClientSuspensions suspensions;

  ConfiguredClients(OAuthProperties properties, ClientSuspensions suspensions) {
    this.clients =
        properties.clients().entrySet().stream()
            .collect(
                toUnmodifiableMap(
                    Map.Entry::getKey, entry -> client(entry.getKey(), entry.getValue())));
    this.suspensions = suspensions;
  }

  @Override
  public void save(RegisteredClient registeredClient) {
    throw new UnsupportedOperationException(
        "Clients are configured, not registered: clientId="
            + registeredClient.getClientId()
            + ", configured="
            + clients.keySet());
  }

  @Override
  public @Nullable RegisteredClient findById(String id) {
    return findByClientId(id);
  }

  @Override
  public @Nullable RegisteredClient findByClientId(String clientId) {
    var client = clients.get(clientId);
    if (client == null || suspensions.isSuspended(clientId)) {
      return null;
    }
    return client;
  }

  private static RegisteredClient client(String clientId, OAuthProperties.Client properties) {
    return RegisteredClient.withId(clientId)
        .clientId(clientId)
        .clientName(properties.name())
        .clientAuthenticationMethod(PRIVATE_KEY_JWT)
        .authorizationGrantType(AUTHORIZATION_CODE)
        .authorizationGrantType(REFRESH_TOKEN)
        .redirectUris(redirectUris -> redirectUris.addAll(properties.redirectUris()))
        .scopes(scopes -> scopes.addAll(properties.scopes()))
        .clientSettings(
            ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(false)
                .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.RS256)
                .build())
        .tokenSettings(
            TokenSettings.builder()
                .authorizationCodeTimeToLive(AUTHORIZATION_CODE_LIFETIME)
                .accessTokenTimeToLive(ACCESS_TOKEN_LIFETIME)
                .refreshTokenTimeToLive(GrantLifetime.IDLE_LIMIT)
                .reuseRefreshTokens(false)
                .build())
        .build();
  }
}
