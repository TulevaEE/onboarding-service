package ee.tuleva.onboarding.auth.browser;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RememberedBrowsers {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  Optional<RememberedBrowser> findUnexpired(String tokenHash) {
    return jdbcClient
        .sql(
            """
            SELECT id, expires_at FROM remembered_browser
            WHERE token_hash = :tokenHash AND expires_at > :now
            """)
        .param("tokenHash", tokenHash)
        .param("now", Timestamp.from(Instant.now(clock)))
        .query(
            (rs, rowNum) ->
                new RememberedBrowser(rs.getLong("id"), rs.getTimestamp("expires_at").toInstant()))
        .optional();
  }

  long add(String tokenHash, Instant expiresAt) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO remembered_browser (token_hash, expires_at)
            VALUES (:tokenHash, :expiresAt)
            """)
        .param("tokenHash", tokenHash)
        .param("expiresAt", Timestamp.from(expiresAt))
        .update(keyHolder, "id");
    return Objects.requireNonNull(keyHolder.getKeyAs(Long.class), "No id for remembered browser");
  }

  boolean rotate(long id, String currentTokenHash, String newTokenHash) {
    return jdbcClient
            .sql(
                """
                UPDATE remembered_browser SET token_hash = :newTokenHash
                WHERE id = :id AND token_hash = :currentTokenHash
                """)
            .param("id", id)
            .param("currentTokenHash", currentTokenHash)
            .param("newTokenHash", newTokenHash)
            .update()
        == 1;
  }

  void extendUntil(long id, Instant expiresAt) {
    jdbcClient
        .sql(
            """
            UPDATE remembered_browser SET expires_at = :expiresAt
            WHERE id = :id AND expires_at < :expiresAt
            """)
        .param("id", id)
        .param("expiresAt", Timestamp.from(expiresAt))
        .update();
  }

  boolean claimLoginStart(long id, PushLogin pushLogin, Duration minimumInterval) {
    Instant now = Instant.now(clock);
    String startedAt = pushLogin.startedAtColumn;
    return jdbcClient
            .sql(
                "UPDATE remembered_browser SET "
                    + startedAt
                    + " = :now WHERE id = :id AND expires_at > :now AND ("
                    + startedAt
                    + " IS NULL OR "
                    + startedAt
                    + " <= :previousStartedBy)")
            .param("id", id)
            .param("now", Timestamp.from(now))
            .param("previousStartedBy", Timestamp.from(now.minus(minimumInterval)))
            .update()
        == 1;
  }

  int removeExpired() {
    return jdbcClient
        .sql("DELETE FROM remembered_browser WHERE expires_at <= :now")
        .param("now", Timestamp.from(Instant.now(clock)))
        .update();
  }
}
