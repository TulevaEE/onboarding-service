package ee.tuleva.onboarding.error

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.web.DefaultErrorAttributes
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import javax.servlet.RequestDispatcher

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@WebMvcTest(controllers = ErrorHandlingController.class)
@WithMockUser
@Import(DefaultErrorAttributes.class)
class ErrorHandlingControllerSpec extends Specification {

	@Autowired
	MockMvc mvc

	def "error handling works"() {
		expect:
		mvc.perform(get("/error")
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
				.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/asdf")
				.requestAttr(RequestDispatcher.ERROR_MESSAGE, "oops!"))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
				.andExpect(jsonPath('$.status', is(403)))
				.andExpect(jsonPath('$.error', is("Forbidden")))
				.andExpect(jsonPath('$.message', is("oops!")))
	}

}
