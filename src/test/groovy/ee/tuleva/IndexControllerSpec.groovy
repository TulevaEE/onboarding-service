package ee.tuleva

import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl

class IndexControllerSpec extends Specification {

	MockMvc mvc = MockMvcBuilders.standaloneSetup(new IndexController()).build()

	def "root redirects to the swagger-ui"() {
		expect:
		mvc.perform(get("/"))
				.andExpect(redirectedUrl("/swagger-ui.html"));
	}

}
