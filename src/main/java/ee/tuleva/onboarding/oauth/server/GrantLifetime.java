package ee.tuleva.onboarding.oauth.server;

import java.time.Duration;
import java.time.Instant;

final class GrantLifetime {

  static final Duration ABSOLUTE_LIMIT = Duration.ofDays(180);
  static final Duration IDLE_LIMIT = Duration.ofDays(30);

  private GrantLifetime() {}

  static Instant expiresAt(Instant grantedAt) {
    return grantedAt.plus(ABSOLUTE_LIMIT);
  }

  static Instant endsAt(Instant expiresAt, Instant lastRefreshedAt) {
    var idleEnd = lastRefreshedAt.plus(IDLE_LIMIT);
    return idleEnd.isBefore(expiresAt) ? idleEnd : expiresAt;
  }

  static boolean isLive(Instant expiresAt, Instant lastRefreshedAt, Instant now) {
    return now.isBefore(endsAt(expiresAt, lastRefreshedAt));
  }
}
