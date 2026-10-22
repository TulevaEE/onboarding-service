package ee.tuleva.onboarding.oauth.server;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.oauth2.core.endpoint.PkceParameterNames.CODE_CHALLENGE;
import static org.springframework.security.oauth2.core.endpoint.PkceParameterNames.CODE_CHALLENGE_METHOD;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Component;

@Component
class AuthorizationCodeIssuer {

  private final AuthorizationServerSettings settings;
  private final OAuth2AuthorizationCodeRequestAuthenticationProvider provider;

  AuthorizationCodeIssuer(
      ConfiguredClients clients,
      OAuth2AuthorizationService authorizationService,
      AuthorizationServerSettings settings) {
    this.settings = settings;
    this.provider =
        new OAuth2AuthorizationCodeRequestAuthenticationProvider(
            clients, authorizationService, new InMemoryOAuth2AuthorizationConsentService());
    this.provider.setAuthorizationConsentRequired(consentGivenOnTheConnectPage -> false);
    AuthorizationRequestRules.enforceOn(this.provider);
  }

  String issue(PendingAuthorization request, UUID subject, String personalCode) {
    var codeRequest =
        new OAuth2AuthorizationCodeRequestAuthenticationToken(
            requireNonNull(settings.getIssuer()) + settings.getAuthorizationEndpoint(),
            request.clientId(),
            ConnectedPerson.authenticated(subject, personalCode),
            request.redirectUri(),
            request.state(),
            request.scopes(),
            Map.of(
                CODE_CHALLENGE, request.codeChallenge(),
                CODE_CHALLENGE_METHOD, request.codeChallengeMethod()));
    AuthorizationServerContextHolder.setContext(authorizationServerContext());
    try {
      var issued =
          (OAuth2AuthorizationCodeRequestAuthenticationToken) provider.authenticate(codeRequest);
      return requireNonNull(issued.getAuthorizationCode()).getTokenValue();
    } finally {
      AuthorizationServerContextHolder.resetContext();
    }
  }

  private AuthorizationServerContext authorizationServerContext() {
    return new AuthorizationServerContext() {
      @Override
      public String getIssuer() {
        return requireNonNull(settings.getIssuer());
      }

      @Override
      public AuthorizationServerSettings getAuthorizationServerSettings() {
        return settings;
      }
    };
  }
}
