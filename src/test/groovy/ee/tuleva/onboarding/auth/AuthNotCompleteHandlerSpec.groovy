package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import spock.lang.Specification

class AuthNotCompleteHandlerSpec extends Specification {
  AuthNotCompleteHandler authNotCompleteHandler

  def setup() {
    authNotCompleteHandler = new AuthNotCompleteHandler()
  }

  def "returns bad request when exception"() {
    given:
    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    when:
    authNotCompleteHandler.onAuthenticationFailure(request, response, new OAuth2AuthenticationException("error"))
    then:
    response.status == HttpStatus.BAD_REQUEST.value()
    response.getContentAsString() == "{\"error\":\"error\"}"
  }

  def "returns ok when auth not complete"() {
    given:
    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    when:
    authNotCompleteHandler.onAuthenticationFailure(request, response, new AuthNotCompleteException())
    then:
    response.status == HttpStatus.OK.value()
    response.getContentAsString() == "{\"error\":\"AUTHENTICATION_NOT_COMPLETE\"}"
  }
}
