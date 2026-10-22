package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.auth.authority.Authority.CONNECTED_APP;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.role.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class ConnectedAppAuthenticatorTest {

  private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
  private static final UUID GRANT_ID = UUID.fromString("5b0c1d2e-3f4a-4b5c-8d6e-7f8a9b0c1d2e");
  private static final String PERSONAL_CODE = "38812121215";
  private static final AuthenticatedPerson PERSON_ACTING_AS_THEMSELVES =
      AuthenticatedPerson.builder()
          .personalCode(PERSONAL_CODE)
          .firstName("Jordan")
          .lastName("Valdma")
          .userId(7L)
          .role(new Role(PERSON, PERSONAL_CODE, "Jordan Valdma"))
          .build();

  private final Grants grants = mock(Grants.class);
  private final RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
  private final PrincipalService principalService = mock(PrincipalService.class);
  private final ConnectedAppAuthenticator authenticator =
      new ConnectedAppAuthenticator(grants, clients, principalService);

  private final GrantRow grant = grantOf(Set.of("balances:read", "transactions:read"));

  @Test
  void aLiveGrantAuthenticatesThePersonBehindItWithNoCredentialsAndOnlyTheScopesOfTheToken() {
    givenALiveGrantOfARegisteredAppForAnActiveUser();

    var authentication = authenticator.authenticate(accessToken(List.of("balances:read")));

    assertThat(authentication).isEqualTo(appAuthenticationWith(List.of("balances:read")));
  }

  @Test
  void aScopeTulevaNoLongerAllowsTheAppOpensNothingWhileTheGrantStillHoldsIt() {
    givenALiveGrantOfARegisteredAppForAnActiveUser();
    given(clients.findByClientId("portfellow")).willReturn(registeredClientWith("balances:read"));

    var authentication =
        authenticator.authenticate(accessToken(List.of("balances:read", "transactions:read")));

    assertThat(authentication).isEqualTo(appAuthenticationWith(List.of("balances:read")));
  }

  @Test
  void aGrantThatHasEndedAuthenticatesNobody() {
    givenALiveGrantOfARegisteredAppForAnActiveUser();
    given(grants.isLive(grant)).willReturn(false);

    assertThatThrownBy(() -> authenticator.authenticate(accessToken(List.of("balances:read"))))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  void aGrantTulevaNoLongerKnowsAuthenticatesNobody() {
    givenALiveGrantOfARegisteredAppForAnActiveUser();
    given(grants.byId(GRANT_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> authenticator.authenticate(accessToken(List.of("balances:read"))))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  void aGrantOfASuspendedAppAuthenticatesNobody() {
    givenALiveGrantOfARegisteredAppForAnActiveUser();
    given(clients.findByClientId("portfellow")).willReturn(null);

    assertThatThrownBy(() -> authenticator.authenticate(accessToken(List.of("balances:read"))))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  void aGrantOfAPersonWhoIsNoLongerAnActiveUserAuthenticatesNobody() {
    givenALiveGrantOfARegisteredAppForAnActiveUser();
    given(principalService.findActingAsThemselves(PERSONAL_CODE)).willReturn(Optional.empty());

    assertThatThrownBy(() -> authenticator.authenticate(accessToken(List.of("balances:read"))))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  private void givenALiveGrantOfARegisteredAppForAnActiveUser() {
    given(grants.byId(GRANT_ID)).willReturn(Optional.of(grant));
    given(grants.isLive(grant)).willReturn(true);
    given(clients.findByClientId("portfellow"))
        .willReturn(registeredClientWith("balances:read", "transactions:read"));
    given(principalService.findActingAsThemselves(PERSONAL_CODE))
        .willReturn(Optional.of(PERSON_ACTING_AS_THEMSELVES));
  }

  private static RegisteredClient registeredClientWith(String... scopes) {
    return RegisteredClient.withId("portfellow")
        .clientId("portfellow")
        .authorizationGrantType(AUTHORIZATION_CODE)
        .redirectUri("https://app.example/callback")
        .scopes(configured -> configured.addAll(List.of(scopes)))
        .build();
  }

  private static UsernamePasswordAuthenticationToken appAuthenticationWith(List<String> scopes) {
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            PERSON_ACTING_AS_THEMSELVES,
            null,
            Stream.concat(Stream.of(CONNECTED_APP), scopes.stream().map("SCOPE_"::concat))
                .map(SimpleGrantedAuthority::new)
                .toList());
    authentication.setDetails(new ConnectedAppAccess("portfellow", GRANT_ID, scopes));
    return authentication;
  }

  private static Jwt accessToken(List<String> scopes) {
    return Jwt.withTokenValue("access-token")
        .header("alg", "ES384")
        .subject("0b9a7c55-7f4e-4a59-8a7b-3c2d1e0f9a88")
        .claim("scope", scopes)
        .claim("grant_id", GRANT_ID.toString())
        .issuedAt(NOW)
        .expiresAt(NOW.plusSeconds(600))
        .build();
  }

  private static GrantRow grantOf(Set<String> scopes) {
    return new GrantRow(
        GRANT_ID,
        "portfellow",
        UUID.fromString("0b9a7c55-7f4e-4a59-8a7b-3c2d1e0f9a88"),
        PERSONAL_CODE,
        scopes,
        "https://app.example/callback",
        "client-state",
        "challenge",
        "S256",
        NOW.minus(10, DAYS),
        NOW.plus(170, DAYS),
        NOW.minus(1, DAYS),
        null,
        null,
        null,
        true,
        "hash-of-the-access-token",
        NOW,
        NOW.plusSeconds(600),
        "hash-of-the-refresh-token",
        NOW,
        NOW.plus(30, DAYS),
        null);
  }
}
