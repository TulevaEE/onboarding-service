package ee.tuleva.onboarding.oauth.server;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@Import({OAuthHousekeeping.class, OAuthHousekeepingTest.FixedClockConfig.class})
@Transactional
class OAuthHousekeepingTest {

  private static final Instant NOW = Instant.parse("2026-10-08T03:20:00Z");

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, UTC);
    }
  }

  @Autowired private OAuthHousekeeping housekeeping;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void aPendingRequestOlderThanADayIsDeletedAndAFreshOneKept() {
    pendingAuthorization(NOW.minus(25, HOURS));
    var fresh = pendingAuthorization(NOW.minus(1, HOURS));

    housekeeping.purge();

    assertThat(pendingAuthorizationIds()).containsExactly(fresh);
  }

  @Test
  void spentRefreshTokensOfRevokedOrEndedGrantsAreDeletedAndThoseOfLiveGrantsKept() {
    var subject = subject();
    var revoked = grant(subject, NOW.minus(10, DAYS), NOW.minus(1, DAYS), NOW.minus(1, HOURS));
    var expired = grant(subject, NOW.minus(181, DAYS), NOW.minus(2, DAYS), null);
    var idle = grant(subject, NOW.minus(40, DAYS), NOW.minus(31, DAYS), null);
    var live = grant(subject, NOW.minus(10, DAYS), NOW.minus(1, DAYS), null);
    spentRefreshToken("revoked-spent", revoked);
    spentRefreshToken("expired-spent", expired);
    spentRefreshToken("idle-spent", idle);
    spentRefreshToken("live-spent", live);

    housekeeping.purge();

    assertThat(spentRefreshTokenHashes()).containsExactly("live-spent");
  }

  @Test
  void grantsThemselvesAreKept() {
    var subject = subject();
    grant(subject, NOW.minus(181, DAYS), NOW.minus(2, DAYS), NOW.minus(1, DAYS));

    housekeeping.purge();

    assertThat(jdbcClient.sql("SELECT COUNT(*) FROM oauth_grant").query(Long.class).single())
        .isEqualTo(1L);
  }

  private UUID pendingAuthorization(Instant createdAt) {
    var id = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO oauth_pending_authorization (id, client_id, redirect_uri, scopes,
              code_challenge, code_challenge_method, browser_binding_hash, created_at)
            VALUES (:id, 'portfellow', 'https://app.example/callback', 'balances:read',
              'challenge', 'S256', 'binding-hash', :createdAt)
            """)
        .param("id", id)
        .param("createdAt", Timestamps.of(createdAt))
        .update();
    return id;
  }

  private UUID subject() {
    var id = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO oauth_subject (id, client_id, personal_code, created_at)
            VALUES (:id, :clientId, '38812121215', :createdAt)
            """)
        .param("id", id)
        .param("clientId", "housekeeping-" + id)
        .param("createdAt", Timestamps.of(NOW.minus(200, DAYS)))
        .update();
    return id;
  }

  private UUID grant(
      UUID subject, Instant grantedAt, Instant lastRefreshedAt, @Nullable Instant revokedAt) {
    var id = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO oauth_grant (id, client_id, subject_id, personal_code, scopes, redirect_uri,
              code_challenge, code_challenge_method, granted_at, expires_at, last_refreshed_at,
              authorization_code_used, revoked_at)
            VALUES (:id, 'portfellow', :subject, '38812121215', 'balances:read',
              'https://app.example/callback', 'challenge', 'S256', :grantedAt, :expiresAt,
              :lastRefreshedAt, TRUE, :revokedAt)
            """)
        .param("id", id)
        .param("subject", subject)
        .param("grantedAt", Timestamps.of(grantedAt))
        .param("expiresAt", Timestamps.of(GrantLifetime.expiresAt(grantedAt)))
        .param("lastRefreshedAt", Timestamps.of(lastRefreshedAt))
        .param("revokedAt", Timestamps.ofNullable(revokedAt), Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
    return id;
  }

  private void spentRefreshToken(String hash, UUID grant) {
    jdbcClient
        .sql(
            """
            INSERT INTO oauth_spent_refresh_token (token_hash, grant_id, spent_at)
            VALUES (:hash, :grant, :spentAt)
            """)
        .param("hash", hash)
        .param("grant", grant)
        .param("spentAt", Timestamps.of(NOW.minus(3, DAYS)))
        .update();
  }

  private List<UUID> pendingAuthorizationIds() {
    return jdbcClient.sql("SELECT id FROM oauth_pending_authorization").query(UUID.class).list();
  }

  private List<String> spentRefreshTokenHashes() {
    return jdbcClient
        .sql("SELECT token_hash FROM oauth_spent_refresh_token")
        .query(String.class)
        .list();
  }
}
