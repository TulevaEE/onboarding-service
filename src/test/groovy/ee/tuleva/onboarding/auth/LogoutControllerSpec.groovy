package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.core.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class LogoutControllerSpec extends BaseControllerSpec {

  private OAuth2AuthorizationService authorizationService = Mock(OAuth2AuthorizationService)
  private LogoutController controller = new LogoutController(authorizationService)
  private MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def 'Removes token when logging out'() {
    def token = Mock(OAuth2Authorization)
    given:
    1 * authorizationService.findByToken("dummy", OAuth2TokenType.ACCESS_TOKEN) >> token
    1 * authorizationService.remove(token)
    when:
    MockHttpServletResponse response = mockMvc
      .perform(get("/v1/logout")
        .header("Authorization", "Bearer dummy")).andReturn().response
    then:
    response.status == HttpStatus.OK.value()
  }
}
