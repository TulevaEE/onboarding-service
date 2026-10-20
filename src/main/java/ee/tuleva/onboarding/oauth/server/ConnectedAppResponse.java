package ee.tuleva.onboarding.oauth.server;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

record ConnectedAppResponse(
    UUID id,
    String clientName,
    List<String> scopes,
    Instant connectedAt,
    @Nullable Instant lastUsedAt,
    Instant expiresAt) {

  static ConnectedAppResponse of(GrantRow grant, RegisteredClient client) {
    return new ConnectedAppResponse(
        grant.id(),
        client.getClientName(),
        grant.scopes().stream().sorted().toList(),
        grant.grantedAt(),
        grant.accessTokenIssuedAt(),
        GrantLifetime.endsAt(grant.expiresAt(), grant.lastRefreshedAt()));
  }
}
