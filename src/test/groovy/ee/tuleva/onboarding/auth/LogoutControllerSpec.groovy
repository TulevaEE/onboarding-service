package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.security.oauth2.provider.token.TokenStore
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class LogoutControllerSpec extends BaseControllerSpec {

  private TokenStore tokenStore = Mock(TokenStore)
  private LogoutController controller = new LogoutController(tokenStore)
  private MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def 'Removes token when logging out'() {
    def token = Mock(OAuth2AccessToken)
    given:
    1 * tokenStore.readAccessToken("dummy") >> token
    1 * tokenStore.removeAccessToken(token)
    when:
    MockHttpServletResponse response = mockMvc
      .perform(get("/v1/logout")
        .header("Authorization", "Bearer dummy")).andReturn().response
    then:
    response.status == HttpStatus.OK.value()
  }
}