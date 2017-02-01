package ee.tuleva

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl

@WebMvcTest(controllers = IndexController, secure = false)
class IndexControllerSpec extends Specification {

	@Autowired
	MockMvc mvc

	def "root redirects to the swagger-ui"() {
		expect:
		mvc.perform(get("/"))
				.andExpect(redirectedUrl("/swagger-ui.html"));
	}

}
