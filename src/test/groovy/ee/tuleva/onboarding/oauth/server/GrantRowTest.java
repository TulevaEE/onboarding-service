package ee.tuleva.onboarding.oauth.server;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GrantRowTest {

  private static final Instant GRANTED = Instant.parse("2026-10-08T09:00:00Z");

  @Test
  void aGrantRefreshedWithinItsLifetimeAndNeverRevokedIsLive() {
    var grant = grant(GRANTED.plus(10, DAYS), null);

    assertThat(grant.isLiveAt(GRANTED.plus(20, DAYS))).isTrue();
  }

  @Test
  void aRevokedGrantIsNotLiveEvenWithinItsLifetime() {
    var grant = grant(GRANTED.plus(10, DAYS), GRANTED.plus(11, DAYS));

    assertThat(grant.isLiveAt(GRANTED.plus(20, DAYS))).isFalse();
  }

  @Test
  void aGrantNotRefreshedForThirtyDaysIsNotLive() {
    var grant = grant(GRANTED.plus(10, DAYS), null);

    assertThat(grant.isLiveAt(GRANTED.plus(40, DAYS))).isFalse();
  }

  @Test
  void aGrantPastItsExpiryIsNotLiveEvenWhenJustRefreshed() {
    var grant = grant(GRANTED.plus(179, DAYS), null);

    assertThat(grant.isLiveAt(GRANTED.plus(180, DAYS))).isFalse();
  }

  private static GrantRow grant(Instant lastRefreshedAt, @Nullable Instant revokedAt) {
    return new GrantRow(
        UUID.fromString("7d4c4d5e-35a4-4d1b-9a8e-2f0b6f7c1a11"),
        "portfellow",
        UUID.fromString("0b9a7c55-7f4e-4a59-8a7b-3c2d1e0f9a88"),
        "38812121215",
        Set.of("balances:read"),
        "https://app.example/callback",
        "client-state",
        "challenge",
        "S256",
        GRANTED,
        GrantLifetime.expiresAt(GRANTED),
        lastRefreshedAt,
        null,
        null,
        null,
        true,
        null,
        null,
        null,
        "hash-of-the-refresh-token",
        lastRefreshedAt,
        lastRefreshedAt.plus(30, DAYS),
        revokedAt);
  }
}
