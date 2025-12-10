package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueCsvExporter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import java.util.stream.Collectors

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FundControllerSpec extends BaseControllerSpec {

    FundService fundService = Mock(FundService)
    FundValueCsvExporter csvExporter = Mock(FundValueCsvExporter)
    FundController controller = new FundController(fundService, csvExporter)

    private MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "get: Get all funds"() {
        given:
        def language = "et"
        1 * fundService.getFunds(Optional.empty()) >> sampleFunds()
        expect:
        mockMvc
                .perform(get("/v1/funds").header("Accept-Language", language))

                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(sampleFunds().size())))
    }

    def "get: Get all funds defaults to et"() {
        given:
        def language = "et"
        1 * fundService.getFunds(Optional.empty()) >> sampleFunds()
        expect:
        mockMvc
            .perform(get("/v1/funds"))

            .andExpect(status().isOk())
            .andExpect(jsonPath('$', hasSize(sampleFunds().size())))
    }

    def "get: Get all funds by manager name"() {
        given:
        String fundManagerName = "Tuleva"
        def language = "et"
        Iterable<Fund> funds = sampleFunds().stream().filter( { f -> f.fundManager.name == fundManagerName}).collect(Collectors.toList())
        1 * fundService.getFunds(Optional.of(fundManagerName)) >> funds
        expect:
        mockMvc
                .perform(get("/v1/funds?fundManager.name=" + fundManagerName).header("Accept-Language", language))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(funds.size())))
                .andExpect(jsonPath('$[0].fundManager.name', is(fundManagerName)))
    }
}
