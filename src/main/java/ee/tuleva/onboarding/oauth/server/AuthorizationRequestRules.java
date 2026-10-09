package ee.tuleva.onboarding.oauth.server;

import static org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR;
import static org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR;

import java.util.function.Consumer;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.util.StringUtils;

final class AuthorizationRequestRules {

  static final Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> VALIDATOR =
      ((Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext>)
              AuthorizationRequestRules::requireRedirectUri)
          .andThen(DEFAULT_REDIRECT_URI_VALIDATOR)
          .andThen(DEFAULT_SCOPE_VALIDATOR)
          .andThen(AuthorizationRequestRules::requireScope);

  private static final String ERROR_URI =
      "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1";

  private AuthorizationRequestRules() {}

  private static void requireRedirectUri(
      OAuth2AuthorizationCodeRequestAuthenticationContext context) {
    var request = context.<OAuth2AuthorizationCodeRequestAuthenticationToken>getAuthentication();
    if (!StringUtils.hasText(request.getRedirectUri())) {
      throw new OAuth2AuthorizationCodeRequestAuthenticationException(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_REQUEST, "OAuth 2.0 Parameter: redirect_uri", ERROR_URI),
          request);
    }
  }

  private static void requireScope(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
    var request = context.<OAuth2AuthorizationCodeRequestAuthenticationToken>getAuthentication();
    if (request.getScopes().isEmpty()) {
      throw new OAuth2AuthorizationCodeRequestAuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE, "OAuth 2.0 Parameter: scope", ERROR_URI),
          request);
    }
  }
}
