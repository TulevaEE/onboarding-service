package ee.tuleva.onboarding.oauth.server;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.oauth2.core.endpoint.PkceParameterNames.CODE_CHALLENGE;
import static org.springframework.security.oauth2.core.endpoint.PkceParameterNames.CODE_CHALLENGE_METHOD;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
class ConnectService {

  private final PendingAuthorizations pendingAuthorizations;
  private final PairwiseSubjects subjects;
  private final RegisteredClientRepository clients;
  private final AuthorizationServerSettings settings;
  private final OAuth2AuthorizationCodeRequestAuthenticationProvider codeIssuer;

  ConnectService(
      PendingAuthorizations pendingAuthorizations,
      PairwiseSubjects subjects,
      RegisteredClientRepository clients,
      AuthorizationServerSettings settings,
      OAuth2AuthorizationService authorizationService) {
    this.pendingAuthorizations = pendingAuthorizations;
    this.subjects = subjects;
    this.clients = clients;
    this.settings = settings;
    this.codeIssuer =
        new OAuth2AuthorizationCodeRequestAuthenticationProvider(
            clients, authorizationService, new InMemoryOAuth2AuthorizationConsentService());
    this.codeIssuer.setAuthorizationConsentRequired(consentGivenOnTheConnectPage -> false);
    this.codeIssuer.setAuthenticationValidator(AuthorizationRequestRules.VALIDATOR);
  }

  ConnectRequestResponse details(UUID id, String browserBinding) {
    var request = openRequest(id, browserBinding);
    return new ConnectRequestResponse(
        client(request).getClientName(),
        request.scopes().stream().sorted().toList(),
        request.expiresAt());
  }

  @Transactional
  RedirectResponse approve(AuthenticatedPerson person, UUID id, String browserBinding) {
    var request = openRequest(id, browserBinding);
    if (!StrongLogin.asThemselvesSince(person, request.createdAt())) {
      throw ConnectRejectedException.loginRequired();
    }
    consume(request);
    var subject = subjects.subjectFor(request.clientId(), person.getPersonalCode());
    var codeRequest =
        new OAuth2AuthorizationCodeRequestAuthenticationToken(
            requireNonNull(settings.getIssuer()) + settings.getAuthorizationEndpoint(),
            request.clientId(),
            ConnectedPerson.authenticated(subject, person.getPersonalCode()),
            request.redirectUri(),
            request.state(),
            request.scopes(),
            Map.of(
                CODE_CHALLENGE, request.codeChallenge(),
                CODE_CHALLENGE_METHOD, request.codeChallengeMethod()));
    AuthorizationServerContextHolder.setContext(authorizationServerContext());
    try {
      var issued =
          (OAuth2AuthorizationCodeRequestAuthenticationToken) codeIssuer.authenticate(codeRequest);
      return redirect(
          request, Map.of("code", requireNonNull(issued.getAuthorizationCode()).getTokenValue()));
    } finally {
      AuthorizationServerContextHolder.resetContext();
    }
  }

  @Transactional
  RedirectResponse deny(UUID id, String browserBinding) {
    var request = openRequest(id, browserBinding);
    consume(request);
    return redirect(request, Map.of("error", "access_denied"));
  }

  private PendingAuthorization openRequest(UUID id, String browserBinding) {
    var request =
        pendingAuthorizations
            .findOpen(id, browserBinding)
            .orElseThrow(ConnectRejectedException::requestNotFound);
    client(request);
    return request;
  }

  private RegisteredClient client(PendingAuthorization request) {
    var client = clients.findByClientId(request.clientId());
    if (client == null) {
      throw ConnectRejectedException.requestNotFound();
    }
    return client;
  }

  private void consume(PendingAuthorization request) {
    if (!pendingAuthorizations.consume(request.id())) {
      throw ConnectRejectedException.requestNotFound();
    }
  }

  private static RedirectResponse redirect(
      PendingAuthorization request, Map<String, String> parameters) {
    var values = new LinkedHashMap<String, String>(parameters);
    if (request.state() != null) {
      values.put("state", request.state());
    }
    var uri = UriComponentsBuilder.fromUriString(request.redirectUri());
    values.keySet().forEach(name -> uri.queryParam(name, "{" + name + "}"));
    return new RedirectResponse(uri.encode().buildAndExpand(values).toUriString());
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
