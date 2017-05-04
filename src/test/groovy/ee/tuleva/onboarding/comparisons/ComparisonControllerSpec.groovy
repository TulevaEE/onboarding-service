package ee.tuleva.onboarding.comparisons

import ee.tuleva.onboarding.UserProvidedControllerSpec
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ComparisonControllerSpec extends UserProvidedControllerSpec {

    def comparisonService = Mock(ComparisonService)
    MockMvc mvc

    def setup() {
        mvc = mockMvc(new ComparisonController(comparisonService))
    }

    def "comparison endpoint works" () {
        when:
        comparisonService.compare(*_) >> ComparisonResponse.builder().currentFundFee(123).build()

        then:
        mvc.perform(get("/v1/comparisons/?monthlyWage=2000&isinTo=EE3600019816"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.currentFundFee', is(123)))
    }

}