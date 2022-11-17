package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.authority.Authority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember
import static ee.tuleva.onboarding.time.TestClockHolder.now
import static org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER

class OAuth2Fixture {

  public static PersonalCodeAuthentication aPersonalCodeAuthentication() {
    return new PersonalCodeAuthentication(
        "client-id",
        GrantType.SMART_ID,
        sampleAuthenticatedPersonNonMember().build(),
        List.of(new SimpleGrantedAuthority(Authority.USER)),
        new AuthenticationAttributes(),
    )
  }

  public static OAuth2AccessToken anAccessToken = new OAuth2AccessToken(BEARER, "dummy", now, now.plusSeconds(3600))

  public static ClientRegistration aClientRegistration = ClientRegistration
      .withRegistrationId("id")
      .clientId("client-id")
      .clientSecret("clientsecret")
      .redirectUri("https://example.com")
      .authorizationUri("https://example.com/authorization")
      .tokenUri("https://example.com/token")
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .build()

  public static RegisteredClient aRegisteredClient = RegisteredClient.withId("id")
      .clientId("client-id")
      .clientSecret("clientsecret")
      .redirectUri("https://example.com")
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .build()

  public static OAuth2AuthorizedClient anAuthorizedClient(ClientRegistration clientRegistration = aClientRegistration) {
    return new OAuth2AuthorizedClient(
        clientRegistration,
        "38812121215",
        anAccessToken,
        null)
  }
}
