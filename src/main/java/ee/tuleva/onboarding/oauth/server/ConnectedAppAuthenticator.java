package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static ee.tuleva.onboarding.oauth.server.AccessTokenClaims.GRANT_ID_CLAIM;
import static java.util.Objects.requireNonNull;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.SCOPE;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ConnectedAppAuthenticator {

  private final Grants grants;
  private final ConfiguredClients clients;
  private final UserService users;
  private final Clock clock;

  AbstractAuthenticationToken authenticate(Jwt accessToken) {
    var grant = liveGrantOf(accessToken);
    var client = registeredClientOf(grant);
    var person =
        actingAsThemselves(grant.personalCode())
            .orElseThrow(ConnectedAppAuthenticator::connectionEnded);
    var scopes =
        requireNonNull(accessToken.getClaimAsStringList(SCOPE), "Access token without scopes")
            .stream()
            .filter(grant.scopes()::contains)
            .filter(client.getScopes()::contains)
            .sorted()
            .toList();
    var access = new ConnectedAppAccess(grant.clientId(), grant.id(), scopes);
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(person, null, access.authorities());
    authentication.setDetails(access);
    return authentication;
  }

  private GrantRow liveGrantOf(Jwt accessToken) {
    var grantId =
        UUID.fromString(
            requireNonNull(
                accessToken.getClaimAsString(GRANT_ID_CLAIM), "Access token without a grant"));
    return grants
        .byId(grantId)
        .filter(grant -> grant.isLiveAt(clock.instant()))
        .orElseThrow(ConnectedAppAuthenticator::connectionEnded);
  }

  private RegisteredClient registeredClientOf(GrantRow grant) {
    return clients.find(grant.clientId()).orElseThrow(ConnectedAppAuthenticator::connectionEnded);
  }

  private Optional<AuthenticatedPerson> actingAsThemselves(String personalCode) {
    return users
        .findByPersonalCode(personalCode)
        .filter(User::getActive)
        .map(
            user ->
                AuthenticatedPerson.builder()
                    .personalCode(user.getPersonalCode())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .userId(user.getIdOrThrow())
                    .role(new Role(PERSON, user.getPersonalCode(), user.getFullName()))
                    .build());
  }

  private static InvalidBearerTokenException connectionEnded() {
    return new InvalidBearerTokenException("The connection has ended");
  }
}
