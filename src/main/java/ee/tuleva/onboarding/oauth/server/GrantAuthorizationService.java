package ee.tuleva.onboarding.oauth.server;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;
import static org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER;
import static org.springframework.security.oauth2.core.endpoint.PkceParameterNames.CODE_CHALLENGE;
import static org.springframework.security.oauth2.core.endpoint.PkceParameterNames.CODE_CHALLENGE_METHOD;
import static org.springframework.security.oauth2.server.authorization.OAuth2Authorization.Token.INVALIDATED_METADATA_NAME;
import static org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN;
import static org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN;

import ee.tuleva.onboarding.oauth.server.Grants.NewGrant;
import ee.tuleva.onboarding.oauth.server.Grants.RevocationReason;
import ee.tuleva.onboarding.oauth.server.Grants.TokenColumn;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class GrantAuthorizationService implements OAuth2AuthorizationService {

  static final OAuth2TokenType CODE = new OAuth2TokenType(OAuth2ParameterNames.CODE);

  private static final String STORED_AS_HASH = "hashed:";
  static final String LOADED_REFRESH_TOKEN_HASH =
      GrantAuthorizationService.class.getName() + ".loadedRefreshTokenHash";

  private final Grants grants;
  private final RegisteredClientRepository clients;
  private final AuthorizationServerSettings settings;

  @Override
  @Transactional(noRollbackFor = OAuth2AuthenticationException.class)
  public void save(OAuth2Authorization authorization) {
    var id = UUID.fromString(authorization.getId());
    if (grants.byId(id).isPresent()) {
      update(id, authorization);
    } else {
      grants.insert(newGrant(id, authorization));
    }
  }

  @Override
  @Transactional
  public void remove(OAuth2Authorization authorization) {
    grants.revoke(UUID.fromString(authorization.getId()), RevocationReason.REMOVED);
  }

  @Override
  public @Nullable OAuth2Authorization findById(String id) {
    return grants.byId(UUID.fromString(id)).flatMap(row -> active(row, null)).orElse(null);
  }

  @Override
  @Transactional
  public @Nullable OAuth2Authorization findByToken(
      String token, @Nullable OAuth2TokenType tokenType) {
    var hash = TokenHash.of(token);
    var match =
        columnsFor(tokenType)
            .flatMap(
                column ->
                    grants
                        .byTokenHash(column, hash)
                        .map(row -> new Match(row, column, token))
                        .stream())
            .findFirst();
    if (match.isEmpty()) {
      if (tokenType == null || REFRESH_TOKEN.equals(tokenType)) {
        grants
            .grantOfSpentRefreshToken(hash)
            .ifPresent(grantId -> grants.revoke(grantId, RevocationReason.REFRESH_TOKEN_REUSED));
      }
      return null;
    }
    return active(match.get().row(), match.get()).orElse(null);
  }

  private NewGrant newGrant(UUID id, OAuth2Authorization authorization) {
    var person =
        ConnectedPerson.of(requireNonNull(authorization.getAttribute(Principal.class.getName())));
    OAuth2AuthorizationRequest request =
        requireNonNull(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName()));
    var code = requireNonNull(authorization.getToken(OAuth2AuthorizationCode.class)).getToken();
    return new NewGrant(
        id,
        request.getClientId(),
        person.subjectId(),
        person.personalCode(),
        authorization.getAuthorizedScopes(),
        requireNonNull(request.getRedirectUri()),
        request.getState(),
        (String) requireNonNull(request.getAdditionalParameters().get(CODE_CHALLENGE)),
        (String) requireNonNull(request.getAdditionalParameters().get(CODE_CHALLENGE_METHOD)),
        TokenHash.of(code.getTokenValue()),
        code.getIssuedAt(),
        code.getExpiresAt());
  }

  private void update(UUID id, OAuth2Authorization authorization) {
    if (isInvalidated(authorization.getAccessToken())
        || isInvalidated(authorization.getRefreshToken())) {
      grants.revoke(id, RevocationReason.TOKEN_INVALIDATED);
      return;
    }
    rotateRefreshTokenIfNewlyIssued(id, authorization);
    writeAccessTokenIfNewlyIssued(id, authorization.getAccessToken());
    markAuthorizationCodeUsedIfInvalidated(
        id, authorization.getToken(OAuth2AuthorizationCode.class));
  }

  private void rotateRefreshTokenIfNewlyIssued(UUID id, OAuth2Authorization authorization) {
    var refreshToken = authorization.getRefreshToken();
    if (refreshToken == null || !isNewlyIssued(refreshToken.getToken())) {
      return;
    }
    var rotated =
        grants.rotateRefreshToken(
            id,
            TokenHash.of(refreshToken.getToken().getTokenValue()),
            refreshToken.getToken().getIssuedAt(),
            refreshToken.getToken().getExpiresAt(),
            authorization.getAttribute(LOADED_REFRESH_TOKEN_HASH));
    if (!rotated) {
      grants.revoke(id, RevocationReason.REFRESH_TOKEN_REUSED);
      throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
    }
  }

  private void writeAccessTokenIfNewlyIssued(
      UUID id, OAuth2Authorization.@Nullable Token<OAuth2AccessToken> accessToken) {
    if (accessToken != null && isNewlyIssued(accessToken.getToken())) {
      grants.writeAccessToken(
          id,
          TokenHash.of(accessToken.getToken().getTokenValue()),
          accessToken.getToken().getIssuedAt(),
          accessToken.getToken().getExpiresAt());
    }
  }

  private void markAuthorizationCodeUsedIfInvalidated(
      UUID id, OAuth2Authorization.@Nullable Token<OAuth2AuthorizationCode> code) {
    if (code != null && code.isInvalidated()) {
      grants.markAuthorizationCodeUsed(id);
    }
  }

  private Optional<OAuth2Authorization> active(GrantRow row, @Nullable Match match) {
    if (!grants.isLive(row)) {
      return Optional.empty();
    }
    return Optional.ofNullable(clients.findById(row.clientId()))
        .map(client -> build(row, client, match));
  }

  private OAuth2Authorization build(GrantRow row, RegisteredClient client, @Nullable Match match) {
    var authorizationRequest =
        OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(
                requireNonNull(settings.getIssuer()) + settings.getAuthorizationEndpoint())
            .clientId(row.clientId())
            .redirectUri(row.redirectUri())
            .scopes(row.scopes())
            .state(row.state())
            .additionalParameters(
                Map.of(
                    CODE_CHALLENGE, row.codeChallenge(),
                    CODE_CHALLENGE_METHOD, row.codeChallengeMethod()))
            .build();
    var builder =
        OAuth2Authorization.withRegisteredClient(client)
            .id(row.id().toString())
            .principalName(row.subjectId().toString())
            .authorizationGrantType(AUTHORIZATION_CODE)
            .authorizedScopes(row.scopes())
            .attribute(
                Principal.class.getName(),
                ConnectedPerson.authenticated(row.subjectId(), row.personalCode()))
            .attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest);
    if (row.authorizationCodeHash() != null) {
      builder.token(
          new OAuth2AuthorizationCode(
              tokenValue(row.authorizationCodeHash(), TokenColumn.AUTHORIZATION_CODE, match),
              requireNonNull(row.authorizationCodeIssuedAt()),
              requireNonNull(row.authorizationCodeExpiresAt())),
          metadata -> metadata.put(INVALIDATED_METADATA_NAME, row.authorizationCodeUsed()));
    }
    if (row.accessTokenHash() != null) {
      builder.token(
          new OAuth2AccessToken(
              BEARER,
              tokenValue(row.accessTokenHash(), TokenColumn.ACCESS_TOKEN, match),
              row.accessTokenIssuedAt(),
              row.accessTokenExpiresAt(),
              row.scopes()));
    }
    if (row.refreshTokenHash() != null) {
      builder
          .token(
              new OAuth2RefreshToken(
                  tokenValue(row.refreshTokenHash(), TokenColumn.REFRESH_TOKEN, match),
                  row.refreshTokenIssuedAt(),
                  row.refreshTokenExpiresAt()))
          .attribute(LOADED_REFRESH_TOKEN_HASH, row.refreshTokenHash());
    }
    return builder.build();
  }

  private static String tokenValue(String hash, TokenColumn column, @Nullable Match match) {
    return match != null && match.column() == column ? match.rawToken() : STORED_AS_HASH + hash;
  }

  private static boolean isNewlyIssued(OAuth2Token token) {
    return !token.getTokenValue().startsWith(STORED_AS_HASH);
  }

  private static boolean isInvalidated(OAuth2Authorization.@Nullable Token<?> token) {
    return token != null && token.isInvalidated();
  }

  private static Stream<TokenColumn> columnsFor(@Nullable OAuth2TokenType tokenType) {
    if (tokenType == null) {
      return Stream.of(TokenColumn.values());
    }
    if (CODE.equals(tokenType)) {
      return Stream.of(TokenColumn.AUTHORIZATION_CODE);
    }
    if (ACCESS_TOKEN.equals(tokenType)) {
      return Stream.of(TokenColumn.ACCESS_TOKEN);
    }
    if (REFRESH_TOKEN.equals(tokenType)) {
      return Stream.of(TokenColumn.REFRESH_TOKEN);
    }
    return Stream.empty();
  }

  private record Match(GrantRow row, TokenColumn column, String rawToken) {}
}
