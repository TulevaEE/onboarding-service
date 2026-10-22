package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.auth.authority.Authority.CONNECTED_APP;
import static ee.tuleva.onboarding.oauth.server.AccessTokenClaims.GRANT_ID_CLAIM;
import static java.util.Objects.requireNonNull;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.SCOPE;

import ee.tuleva.onboarding.auth.principal.PrincipalService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ConnectedAppAuthenticator {

  static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

  private final Grants grants;
  private final RegisteredClientRepository clients;
  private final PrincipalService principalService;

  AbstractAuthenticationToken authenticate(Jwt accessToken) {
    var grant = liveGrantOf(accessToken);
    var client = registeredClientOf(grant);
    var person =
        principalService
            .findActingAsThemselves(grant.personalCode())
            .orElseThrow(ConnectedAppAuthenticator::connectionEnded);
    var scopes =
        requireNonNull(accessToken.getClaimAsStringList(SCOPE), "Access token without scopes")
            .stream()
            .filter(grant.scopes()::contains)
            .filter(client.getScopes()::contains)
            .sorted()
            .toList();
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(person, null, authoritiesOf(scopes));
    authentication.setDetails(new ConnectedAppAccess(grant.clientId(), grant.id(), scopes));
    return authentication;
  }

  private GrantRow liveGrantOf(Jwt accessToken) {
    var grantId =
        UUID.fromString(
            requireNonNull(
                accessToken.getClaimAsString(GRANT_ID_CLAIM), "Access token without a grant"));
    return grants
        .byId(grantId)
        .filter(grants::isLive)
        .orElseThrow(ConnectedAppAuthenticator::connectionEnded);
  }

  private RegisteredClient registeredClientOf(GrantRow grant) {
    return Optional.ofNullable(clients.findByClientId(grant.clientId()))
        .orElseThrow(ConnectedAppAuthenticator::connectionEnded);
  }

  private static List<GrantedAuthority> authoritiesOf(List<String> scopes) {
    return Stream.concat(
            Stream.of(CONNECTED_APP), scopes.stream().map(SCOPE_AUTHORITY_PREFIX::concat))
        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
        .toList();
  }

  private static InvalidBearerTokenException connectionEnded() {
    return new InvalidBearerTokenException("The connection has ended");
  }
}
