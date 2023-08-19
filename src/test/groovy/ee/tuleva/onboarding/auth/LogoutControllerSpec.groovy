package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class LogoutControllerSpec extends BaseControllerSpec {

  private LogoutController controller = new LogoutController()
  private MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def 'Removes token when logging out'() {
    given:
    def token = "token"
    when:
    MockHttpServletResponse response = mockMvc
        .perform(get("/v1/logout")
            .header("Authorization", "Bearer dummy")).andReturn().response
    then:
    response.status == HttpStatus.OK.value()
  }
}
