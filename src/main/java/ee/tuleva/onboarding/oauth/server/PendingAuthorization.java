package ee.tuleva.onboarding.oauth.server;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record PendingAuthorization(
    UUID id,
    String clientId,
    String redirectUri,
    @Nullable String state,
    Set<String> scopes,
    String codeChallenge,
    String codeChallengeMethod,
    Instant createdAt) {

  Instant expiresAt() {
    return createdAt.plus(PendingAuthorizations.LIFETIME);
  }
}
