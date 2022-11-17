package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.authority.Authority
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.time.TestClockHolder
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
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
    1 * registeredClient.getClientId() >> clientId
    1 * authorization.getAccessToken() >> accessTokenToken
    1 * accessTokenToken.getToken() >> accessToken
    1 * accessToken.getExpiresAt() >> expiresAt
    1 * accessToken.getIssuedAt() >> issuedAt
    1 * authorization.getRegisteredClientId() >> "registeredclient"
    1 * authorization.getPrincipalName() >> authenticatedPerson.personalCode
    1 * authorization.getAttribute(AuthenticationAttributes.AUTHENTICATION_ATTRIBUTES_KEY) >> ["demo": "attribute"]
    1 * oAuth2AuthorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN) >> authorization
    1 * principalService.getFrom(authenticatedPerson.personalCode) >> authenticatedPerson
    1 * grantedAuthorityFactory.from(authenticatedPerson) >> authorities
    1 * registeredClientRepository.findById("registeredclient") >> registeredClient

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
