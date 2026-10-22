package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.event.TrackableEventType.APP_CONNECTED;

import ee.tuleva.onboarding.auth.ClientConnection;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.event.RequestOrigin;
import ee.tuleva.onboarding.event.TrackableEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
class ConnectService {

  private final PendingAuthorizations pendingAuthorizations;
  private final PairwiseSubjects subjects;
  private final ConfiguredClients clients;
  private final AuthorizationCodeIssuer codeIssuer;
  private final ApplicationEventPublisher eventPublisher;
  private final ClientConnection clientConnection;

  ConnectRequestResponse details(UUID id, String browserBinding) {
    var request = openRequest(id, browserBinding);
    return new ConnectRequestResponse(
        client(request).getClientName(),
        sortedScopes(request),
        request.createdAt(),
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
    var code = codeIssuer.issue(request, subject, person.getPersonalCode());
    recordConnection(person, request);
    return redirect(request, Map.of("code", code));
  }

  private void recordConnection(AuthenticatedPerson person, PendingAuthorization request) {
    Map<String, @Nullable Object> connection = new HashMap<>();
    connection.put("clientId", request.clientId());
    connection.put("scopes", sortedScopes(request));
    connection.putAll(RequestOrigin.readOnTheRequestThread(clientConnection));
    eventPublisher.publishEvent(new TrackableEvent(person, APP_CONNECTED, connection));
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
    return clients.find(request.clientId()).orElseThrow(ConnectRejectedException::requestNotFound);
  }

  private void consume(PendingAuthorization request) {
    if (!pendingAuthorizations.consume(request.id())) {
      throw ConnectRejectedException.requestNotFound();
    }
  }

  private static List<String> sortedScopes(PendingAuthorization request) {
    return request.scopes().stream().sorted().toList();
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
}
