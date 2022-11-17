package ee.tuleva.onboarding.auth.mobileid

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest

import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.sampleMobileIdSession

class MobileIdAuthenticationConverterSpec extends Specification {
  MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
  PrincipalService principalService = Mock(PrincipalService)
  GenericSessionStore genericSessionStore = Mock(GenericSessionStore)
  GrantedAuthorityFactory grantedAuthorityFactory = Mock(GrantedAuthorityFactory)
  MobileIdAuthenticationConverter mobileIdAuthenticationConverter

  def setup() {
    mobileIdAuthenticationConverter = new MobileIdAuthenticationConverter(principalService, grantedAuthorityFactory, genericSessionStore, mobileIdAuthService)
  }

  def "GetAccessToken: Logging in with no client id fails"() {
    given:
    HttpServletRequest request = new MockHttpServletRequest()
    when:
    def token = mobileIdAuthenticationConverter.convert(request)
    then:
    token == null
  }

  def "GetAccessToken: Logging in with no mobile id session returns exception"() {
    given:
    genericSessionStore.get(MobileIDSession) >> Optional.empty()
    HttpServletRequest request = new MockHttpServletRequest()
    request.addParameter(OAuth2ParameterNames.CLIENT_ID, "test")
    request.addParameter(OAuth2ParameterNames.GRANT_TYPE, "mobile_id")

    when:
    mobileIdAuthenticationConverter.convert(request)
    then:
    thrown MobileIdSessionNotFoundException
  }

  def "GetAccessToken: Logging in without a mobile id session is not allowed"() {
    given:
    HttpServletRequest request = new MockHttpServletRequest()
    request.addParameter(OAuth2ParameterNames.CLIENT_ID, "test")
    request.addParameter(OAuth2ParameterNames.GRANT_TYPE, "mobile_id")
    1 * genericSessionStore.get(MobileIDSession) >> Optional.of(sampleMobileIdSession)
    1 * mobileIdAuthService.isLoginComplete(sampleMobileIdSession) >> false

    when:
    mobileIdAuthenticationConverter.convert(request)
    then:
    thrown AuthNotCompleteException
  }

  def "GetAccessToken: Logging in with user and grant access token"() {
    given:
    1 * genericSessionStore.get(MobileIDSession) >> Optional.of(sampleMobileIdSession)
    1 * mobileIdAuthService.isLoginComplete(sampleMobileIdSession) >> true
    1 * principalService.getFrom({ Person person ->
      person.personalCode == sampleMobileIdSession.personalCode &&
          person.firstName == sampleMobileIdSession.firstName &&
          person.lastName == sampleMobileIdSession.lastName
    }, _) >> AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember().build()
    HttpServletRequest request = new MockHttpServletRequest()
    request.addParameter(OAuth2ParameterNames.CLIENT_ID, "test")
    request.addParameter(OAuth2ParameterNames.GRANT_TYPE, "mobile_id")
    when:
    Authentication token = mobileIdAuthenticationConverter.convert(request)
    then:
    token != null
  }
}
