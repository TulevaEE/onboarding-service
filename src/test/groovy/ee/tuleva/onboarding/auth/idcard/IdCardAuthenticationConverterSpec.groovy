package ee.tuleva.onboarding.auth.idcard

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.idcard.exception.IdCardSessionNotFoundException
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest

class IdCardAuthenticationConverterSpec extends Specification {

  GenericSessionStore genericSessionStore = Mock(GenericSessionStore)
  PrincipalService principalService = Mock(PrincipalService)
  GrantedAuthorityFactory grantedAuthorityFactory = Mock(GrantedAuthorityFactory)
  private IdCardAuthenticationConverter idCardAuthenticationConverter

  def setup() {
    idCardAuthenticationConverter = new IdCardAuthenticationConverter(principalService, grantedAuthorityFactory, genericSessionStore)
  }

  def "GetAccessToken: Logging in with no client id fails"() {
    given:
    HttpServletRequest request = new MockHttpServletRequest()
    when:
    def token = idCardAuthenticationConverter.convert(request)
    then:
    token == null
  }

  def "GetAccessToken: Logging in with no id card session returns exception"() {
    given:
    genericSessionStore.get(IdCardSession) >> Optional.empty()
    HttpServletRequest request = new MockHttpServletRequest()
    request.addParameter(OAuth2ParameterNames.CLIENT_ID, "test")
    request.addParameter(OAuth2ParameterNames.GRANT_TYPE, "id_card")

    when:
    idCardAuthenticationConverter.convert(request)
    then:
    thrown IdCardSessionNotFoundException
  }

  def "GetAccessToken: Logging in with user and grant access token"() {
    given:
    def idCardSession = new IdCardSession("Justin", "Case", "38512121212", IdDocumentType.DIPLOMATIC_ID_CARD);
    1 * genericSessionStore.get(IdCardSession) >> Optional.of(idCardSession)
    1 * principalService.getFrom({ Person person ->
      person.personalCode == idCardSession.personalCode &&
          person.firstName == idCardSession.firstName &&
          person.lastName == idCardSession.lastName
    }, _) >> AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember().build()

    HttpServletRequest request = new MockHttpServletRequest()
    request.addParameter(OAuth2ParameterNames.CLIENT_ID, "test")
    request.addParameter(OAuth2ParameterNames.GRANT_TYPE, "id_card")

    when:
    Authentication authentication = idCardAuthenticationConverter.convert(request)
    then:
    authentication != null
  }
}
