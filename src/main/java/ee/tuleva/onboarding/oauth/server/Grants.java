package ee.tuleva.onboarding.oauth.server;

import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class Grants {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  void insert(NewGrant grant) {
    var grantedAt = clock.instant();
    jdbcClient
        .sql(
            """
            INSERT INTO oauth_grant (id, client_id, subject_id, personal_code, scopes, redirect_uri,
              state, code_challenge, code_challenge_method, granted_at, expires_at, last_refreshed_at,
              authorization_code_hash, authorization_code_issued_at, authorization_code_expires_at,
              authorization_code_used)
            VALUES (:id, :clientId, :subjectId, :personalCode, :scopes, :redirectUri, :state,
              :codeChallenge, :codeChallengeMethod, :grantedAt, :expiresAt, :grantedAt,
              :codeHash, :codeIssuedAt, :codeExpiresAt, FALSE)
            """)
        .param("id", grant.id())
        .param("clientId", grant.clientId())
        .param("subjectId", grant.subjectId())
        .param("personalCode", grant.personalCode())
        .param("scopes", String.join(" ", grant.scopes()))
        .param("redirectUri", grant.redirectUri())
        .param("state", grant.state(), Types.VARCHAR)
        .param("codeChallenge", grant.codeChallenge())
        .param("codeChallengeMethod", grant.codeChallengeMethod())
        .param("grantedAt", Timestamps.of(grantedAt))
        .param("expiresAt", Timestamps.of(GrantLifetime.expiresAt(grantedAt)))
        .param("codeHash", grant.codeHash())
        .param("codeIssuedAt", Timestamps.ofNullable(grant.codeIssuedAt()))
        .param("codeExpiresAt", Timestamps.ofNullable(grant.codeExpiresAt()))
        .update();
    log.info("OAuth grant created: grantId={}, clientId={}", grant.id(), grant.clientId());
  }

  Optional<GrantRow> byId(UUID id) {
    return jdbcClient
        .sql("SELECT * FROM oauth_grant WHERE id = :id")
        .param("id", id)
        .query(GrantRow::map)
        .optional();
  }

  Optional<GrantRow> byTokenHash(TokenColumn column, String hash) {
    return jdbcClient
        .sql("SELECT * FROM oauth_grant WHERE " + column.hashColumn() + " = :hash")
        .param("hash", hash)
        .query(GrantRow::map)
        .optional();
  }

  boolean rotateRefreshToken(
      UUID id,
      String hash,
      @Nullable Instant issuedAt,
      @Nullable Instant expiresAt,
      @Nullable String currentHash) {
    var currentHashMatches =
        currentHash == null ? "refresh_token_hash IS NULL" : "refresh_token_hash = :currentHash";
    var statement =
        jdbcClient
            .sql(
                """
                UPDATE oauth_grant
                SET refresh_token_hash = :hash, refresh_token_issued_at = :issuedAt,
                  refresh_token_expires_at = :expiresAt, last_refreshed_at = :now
                WHERE id = :id AND revoked_at IS NULL AND %s
                """
                    .formatted(currentHashMatches))
            .param("hash", hash)
            .param("issuedAt", Timestamps.ofNullable(issuedAt))
            .param("expiresAt", Timestamps.ofNullable(expiresAt))
            .param("now", Timestamps.of(clock.instant()))
            .param("id", id);
    if (currentHash != null) {
      statement = statement.param("currentHash", currentHash);
    }
    if (statement.update() == 0) {
      return false;
    }
    if (currentHash != null) {
      jdbcClient
          .sql(
              """
              INSERT INTO oauth_spent_refresh_token (token_hash, grant_id, spent_at)
              VALUES (:hash, :grantId, :spentAt)
              """)
          .param("hash", currentHash)
          .param("grantId", id)
          .param("spentAt", Timestamps.of(clock.instant()))
          .update();
    }
    return true;
  }

  void writeAccessToken(
      UUID id, String hash, @Nullable Instant issuedAt, @Nullable Instant expiresAt) {
    jdbcClient
        .sql(
            """
            UPDATE oauth_grant
            SET access_token_hash = :hash, access_token_issued_at = :issuedAt,
              access_token_expires_at = :expiresAt
            WHERE id = :id
            """)
        .param("hash", hash)
        .param("issuedAt", Timestamps.ofNullable(issuedAt))
        .param("expiresAt", Timestamps.ofNullable(expiresAt))
        .param("id", id)
        .update();
  }

  void markAuthorizationCodeUsed(UUID id) {
    jdbcClient
        .sql("UPDATE oauth_grant SET authorization_code_used = TRUE WHERE id = :id")
        .param("id", id)
        .update();
  }

  Optional<UUID> grantOfSpentRefreshToken(String hash) {
    return jdbcClient
        .sql("SELECT grant_id FROM oauth_spent_refresh_token WHERE token_hash = :hash")
        .param("hash", hash)
        .query(UUID.class)
        .optional();
  }

  void revoke(UUID id, RevocationReason reason) {
    var revoked =
        jdbcClient
            .sql(
                """
                UPDATE oauth_grant
                SET revoked_at = :now, revocation_reason = :reason, authorization_code_hash = NULL,
                  access_token_hash = NULL, refresh_token_hash = NULL
                WHERE id = :id AND revoked_at IS NULL
                """)
            .param("now", Timestamps.of(clock.instant()))
            .param("reason", reason.name())
            .param("id", id)
            .update();
    if (revoked == 1) {
      log.info("OAuth grant revoked: grantId={}, reason={}", id, reason);
    }
  }

  boolean isLive(GrantRow row) {
    return row.revokedAt() == null
        && GrantLifetime.isLive(row.expiresAt(), row.lastRefreshedAt(), clock.instant());
  }

  record NewGrant(
      UUID id,
      String clientId,
      UUID subjectId,
      String personalCode,
      Set<String> scopes,
      String redirectUri,
      @Nullable String state,
      String codeChallenge,
      String codeChallengeMethod,
      String codeHash,
      @Nullable Instant codeIssuedAt,
      @Nullable Instant codeExpiresAt) {}

  enum TokenColumn {
    AUTHORIZATION_CODE("authorization_code_hash"),
    ACCESS_TOKEN("access_token_hash"),
    REFRESH_TOKEN("refresh_token_hash");

    private final String hashColumn;

    TokenColumn(String hashColumn) {
      this.hashColumn = hashColumn;
    }

    String hashColumn() {
      return hashColumn;
    }
  }

  enum RevocationReason {
    REMOVED,
    TOKEN_INVALIDATED,
    REFRESH_TOKEN_REUSED
  }
}
