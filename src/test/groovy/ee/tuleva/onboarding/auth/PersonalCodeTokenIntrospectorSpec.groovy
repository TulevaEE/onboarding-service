package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.authority.Authority
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.time.TestClockHolder
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember

class PersonalCodeTokenIntrospectorSpec extends Specification {

  private OAuth2AuthorizationService oAuth2AuthorizationService = Mock()
  private PrincipalService principalService = Mock()
  private RegisteredClientRepository registeredClientRepository = Mock()
  private GrantedAuthorityFactory grantedAuthorityFactory = Mock()
  private PersonalCodeTokenIntrospector personalCodeTokenIntrospector

  def setup() {
    personalCodeTokenIntrospector = new PersonalCodeTokenIntrospector(oAuth2AuthorizationService, principalService, registeredClientRepository, grantedAuthorityFactory)
  }

  def "throws when authorization not found"() {
    when:
    personalCodeTokenIntrospector.introspect("token")
    then:
    thrown(BadOpaqueTokenException)
  }


  def "Fetches and populates token"() {
    given:
    AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(Authority.USER))
    String token = "sometOken"
    String clientId = "client-id"
    RegisteredClient registeredClient = Mock()
    OAuth2Authorization authorization = Mock()
    OAuth2Authorization.Token<OAuth2AccessToken> accessTokenToken = Mock()
    OAuth2AccessToken accessToken = Mock()
    Instant issuedAt = TestClockHolder.now.plusSeconds(12)
    Instant expiresAt = TestClockHolder.now.plusSeconds(50)
    registeredClient.getClientId() >> clientId
    authorization.getAccessToken() >> accessTokenToken
    accessTokenToken.getToken() >> accessToken
    accessToken.getExpiresAt() >> expiresAt
    accessToken.getIssuedAt() >> issuedAt
    authorization.getRegisteredClientId() >> "registeredclient"
    authorization.getPrincipalName() >> authenticatedPerson.personalCode
    authorization.getAttribute(AuthenticationAttributes.AUTHENTICATION_ATTRIBUTES_KEY) >> ["demo": "attribute"]
    authorization.getAuthorizationGrantType() >> new AuthorizationGrantType("smart_id")
    oAuth2AuthorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN) >> authorization
    principalService.getFrom(authenticatedPerson.personalCode) >> authenticatedPerson
    grantedAuthorityFactory.from(authenticatedPerson) >> authorities
    registeredClientRepository.findById("registeredclient") >> registeredClient

    when:
    def principal = personalCodeTokenIntrospector.introspect(token)
    then:
    principal instanceof PersonalCodeAuthentication
    principal.name == authenticatedPerson.personalCode
    principal.authorities == authorities
    principal.attributes["demo"] == "attribute"
    principal.attributes["iat"] == issuedAt
    principal.attributes["exp"] == expiresAt
  }
}
