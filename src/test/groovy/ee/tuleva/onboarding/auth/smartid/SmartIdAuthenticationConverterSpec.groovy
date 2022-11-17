package ee.tuleva.onboarding.auth.smartid

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.anAuthenticationIdentity

class SmartIdAuthenticationConverterSpec extends Specification {
  SmartIdAuthService smartIdAuthService = Mock(SmartIdAuthService)
  PrincipalService principalService = Mock(PrincipalService)
  GrantedAuthorityFactory grantedAuthorityFactory = Mock(GrantedAuthorityFactory)
  SmartIdAuthenticationConverter smartIdAuthenticationConverter

  def setup() {
    smartIdAuthenticationConverter = new SmartIdAuthenticationConverter(smartIdAuthService, principalService, grantedAuthorityFactory)
  }

  def "GetAccessToken: Logging in with unknown client id is not allowed"() {
    given:
    MockHttpServletRequest request = new MockHttpServletRequest()
    when:
    def token = smartIdAuthenticationConverter.convert(request)
    then:
    token == null
  }

  def "GetAccessToken: Logging in without a smart id session is not allowed"() {
    given:
    MockHttpServletRequest request = new MockHttpServletRequest()
    request.addParameter("authenticationHash", "hash")
    request.addParameter(OAuth2ParameterNames.CLIENT_ID, "test")
    request.addParameter(OAuth2ParameterNames.GRANT_TYPE, "smart_id")
    1 * smartIdAuthService.getAuthenticationIdentity("hash") >> Optional.empty()

    when:
    smartIdAuthenticationConverter.convert(request)
    then:
    thrown AuthNotCompleteException
  }

  def "GetAccessToken: Logging in with no smart id session throws exception"() {
    given:
    MockHttpServletRequest request = new MockHttpServletRequest()
    request.addParameter(OAuth2ParameterNames.CLIENT_ID, "test")
    request.addParameter(OAuth2ParameterNames.GRANT_TYPE, "smart_id")

    when:
    smartIdAuthenticationConverter.convert(request)
    then:
    thrown SmartIdSessionNotFoundException
  }

  def "GetAccessToken: Logging in with user and grant access token"() {
    given:
    1 * smartIdAuthService.getAuthenticationIdentity("hash") >> Optional.of(anAuthenticationIdentity)
    1 * principalService.getFrom({ Person person ->
      person.personalCode == SmartIdFixture.personalCode &&
          person.firstName == SmartIdFixture.firstName &&
          person.lastName == SmartIdFixture.lastName
    }, _) >> AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember().build()
    MockHttpServletRequest request = new MockHttpServletRequest()
    request.addParameter(OAuth2ParameterNames.CLIENT_ID, "test")
    request.addParameter(OAuth2ParameterNames.GRANT_TYPE, "smart_id")
    request.addParameter("authenticationHash", "hash")

    when:
    Authentication token = smartIdAuthenticationConverter.convert(request)
    then:
    token != null
  }
}
