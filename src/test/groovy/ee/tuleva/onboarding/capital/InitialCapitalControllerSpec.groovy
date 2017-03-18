package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.user.User
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class InitialCapitalControllerSpec extends BaseControllerSpec {

    private MockMvc mockMvc

    InitialCapitalRepository initialCapitalRepository = Mock(InitialCapitalRepository)
    InitialCapitalController controller = new InitialCapitalController(initialCapitalRepository)

    User sampleUser = new User()
    InitialCapital sampleInitialCapital = new InitialCapital()

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "InitialCapital: Get information about current user initial capital"() {
        given:
        1 * initialCapitalRepository.findByUser(sampleUser) >> sampleInitialCapital
        when:
        MockHttpServletResponse response = mockMvc
                .perform(get("/v1/initial-capital")).andReturn().response
        then:
        response.status == HttpStatus.OK.value()

    }

}
