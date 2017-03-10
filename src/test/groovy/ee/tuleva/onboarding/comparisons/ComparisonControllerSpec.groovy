package ee.tuleva.onboarding.comparisons

import ee.tuleva.onboarding.UserProvidedControllerSpec
import ee.tuleva.onboarding.income.Money
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ComparisonControllerSpec extends UserProvidedControllerSpec {

    def comparisonService = Mock(ComparisonService)
    MockMvc mvc

    def setup() {
        mvc = mockMvcWithAuthenticationPrincipal(new ComparisonController(comparisonService))
    }

    def "comparison endpoint works" () {
        when:
        comparisonService.compare(*_) >> Money.builder().amount(new BigDecimal("123.0")).currency("EUR").build()

        then:
        mvc.perform(get("/v1/comparisons/?monthlyWage=2000&isinTo=EE3600019816"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.amount', is(123.0d)))
    }

    def "invalid parameter bad request sending"() {
        expect:
        mvc.perform(get("/v1/comparisons/?monthlyWage=2000&isinTo=EE36000"))
                .andExpect(status().is4xxClientError())
    }

    def "stateless comparison endpoint"() {
        ComparisonCommand cmd = new ComparisonCommand();
        cmd.setCurrentCapitals([EE3600019832: 10000.0G])
        cmd.setManagementFeeRates([EE3600019832: 0.0075G, AE123232334: 0.0055G])
        cmd.setActiveFundIsin("EE3600019832")
        cmd.setAge(30)
        cmd.setMonthlyWage(1200.0G)

        mvc.perform(get("/v1/comparisons", cmd))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.amount', is(6629.86G)))

    }

}