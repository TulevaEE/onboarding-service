package ee.tuleva.onboarding.oauth.server;

import static java.util.Objects.requireNonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

record GrantRow(
    UUID id,
    String clientId,
    UUID subjectId,
    String personalCode,
    Set<String> scopes,
    String redirectUri,
    @Nullable String state,
    String codeChallenge,
    String codeChallengeMethod,
    Instant expiresAt,
    Instant lastRefreshedAt,
    @Nullable String authorizationCodeHash,
    @Nullable Instant authorizationCodeIssuedAt,
    @Nullable Instant authorizationCodeExpiresAt,
    boolean authorizationCodeUsed,
    @Nullable String accessTokenHash,
    @Nullable Instant accessTokenIssuedAt,
    @Nullable Instant accessTokenExpiresAt,
    @Nullable String refreshTokenHash,
    @Nullable Instant refreshTokenIssuedAt,
    @Nullable Instant refreshTokenExpiresAt,
    @Nullable Instant revokedAt) {

  static GrantRow map(ResultSet resultSet, int rowNum) throws SQLException {
    return new GrantRow(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("client_id"),
        resultSet.getObject("subject_id", UUID.class),
        resultSet.getString("personal_code"),
        Arrays.stream(resultSet.getString("scopes").split(" "))
            .collect(Collectors.toUnmodifiableSet()),
        resultSet.getString("redirect_uri"),
        resultSet.getString("state"),
        resultSet.getString("code_challenge"),
        resultSet.getString("code_challenge_method"),
        requireNonNull(Timestamps.read(resultSet, "expires_at")),
        requireNonNull(Timestamps.read(resultSet, "last_refreshed_at")),
        resultSet.getString("authorization_code_hash"),
        Timestamps.read(resultSet, "authorization_code_issued_at"),
        Timestamps.read(resultSet, "authorization_code_expires_at"),
        resultSet.getBoolean("authorization_code_used"),
        resultSet.getString("access_token_hash"),
        Timestamps.read(resultSet, "access_token_issued_at"),
        Timestamps.read(resultSet, "access_token_expires_at"),
        resultSet.getString("refresh_token_hash"),
        Timestamps.read(resultSet, "refresh_token_issued_at"),
        Timestamps.read(resultSet, "refresh_token_expires_at"),
        Timestamps.read(resultSet, "revoked_at"));
  }
}
