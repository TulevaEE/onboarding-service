package ee.tuleva.onboarding.auth.http


import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.OAuth2Fixture.*

class AuthorizedClientManagerOAuth2InterceptorSpec extends Specification {

  OAuth2AuthorizedClientManager manager = Mock()
  AuthorizedClientManagerOAuth2Interceptor interceptor

  def setup() {
    interceptor = new AuthorizedClientManagerOAuth2Interceptor(manager, aClientRegistration)

    SecurityContext sc = SecurityContextHolder.createEmptyContext()
    sc.authentication = aPersonalCodeAuthentication()
    SecurityContextHolder.context = sc
  }

  def cleanup() {
    SecurityContextHolder.clearContext()
  }

  def "Adds oauth token to the header"() {
    given:
    HttpRequest request = new MockClientHttpRequest()
    ClientHttpRequestExecution execution = Mock()
    byte[] requestBody = []
    byte[] responseBody = []
    ClientHttpResponse response = new MockClientHttpResponse(responseBody, HttpStatus.I_AM_A_TEAPOT)
    1 * execution.execute(request, requestBody) >> response
    1 * manager.authorize(_) >> anAuthorizedClient()
    when:
    def result = interceptor.intercept(request, requestBody, execution)
    then:
    response == result
    request.headers.getFirst("Authorization") == "Bearer dummy"
  }
}
