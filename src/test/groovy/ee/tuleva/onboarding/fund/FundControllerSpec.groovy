package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.mandate.MandateFixture
import org.springframework.test.web.servlet.MockMvc

import java.util.stream.Collectors

import static org.hamcrest.Matchers.hasSize
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FundControllerSpec extends BaseControllerSpec {

    FundRepository fundRepository = Mock(FundRepository)
    FundController controller = new FundController(fundRepository)

    private MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "get: Get all funds"() {
        given:
        1 * fundRepository.findAll() >> MandateFixture.sampleFunds()
        expect:
        mockMvc
                .perform(get("/v1/funds"))

                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(MandateFixture.sampleFunds().size())));
    }

    def "get: Get all funds by manager name"() {
        given:
        String fundManagerName = "Tuleva"
        Iterable<Fund> funds = MandateFixture.sampleFunds().stream().filter( { f -> f.fundManager.name == fundManagerName}).collect(Collectors.toList())
        1 * fundRepository.findByFundManagerNameIgnoreCase(fundManagerName) >> funds
        expect:
        mockMvc
                .perform(get("/v1/funds?fundManager.name=" + fundManagerName))

                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(funds.size())));
    }


}
