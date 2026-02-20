package ee.tuleva.onboarding.error


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static jakarta.servlet.RequestDispatcher.*
import static org.hamcrest.Matchers.is
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@WebMvcTest(ErrorHandlingController)
@WithMockUser
class ErrorHandlingControllerSpec extends Specification {

  @Autowired
  MockMvc mvc

  def "error handling works"() {
    expect:
    mvc.perform(get("/error")
        .requestAttr(ERROR_EXCEPTION, new RuntimeException())
        .requestAttr(ERROR_STATUS_CODE, 403)
        .requestAttr(ERROR_REQUEST_URI, "/asdf")
        .requestAttr(ERROR_MESSAGE, "oops!"))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath('$.errors[0].code', is("Forbidden")))
        .andExpect(jsonPath('$.errors[0].message', is("oops!")))
        .andExpect(jsonPath('$.errors[0].path').doesNotExist())
        .andExpect(jsonPath('$.errors[0].arguments').isEmpty())
  }

}
