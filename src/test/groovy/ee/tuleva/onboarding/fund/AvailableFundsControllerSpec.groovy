package ee.tuleva.onboarding.fund

import ee.tuleva.domain.fund.Fund
import ee.tuleva.domain.fund.FundManager
import ee.tuleva.domain.fund.FundManagerRepository
import ee.tuleva.domain.fund.FundRepository
import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.hasSize
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AvailableFundsControllerSpec extends BaseControllerSpec {

    FundRepository fundRepository = Mock(FundRepository)
    FundManagerRepository fundManagerRepository = Mock(FundManagerRepository)

    AvailableFundsController controller = new AvailableFundsController(fundRepository, fundManagerRepository)

    private MockMvc mockMvc
    String sampleFundManagerName = "Tuleva"
    FundManager sampleFundManager = new FundManager(new Long(1), sampleFundManagerName)

    def setup() {
        mockMvc = getMockMvc(controller)
    }

    def "InitialCapital: "() {
        given:
        1 * fundManagerRepository.findByName(sampleFundManagerName) >> sampleFundManager
        1 * fundRepository.findByFundManager(sampleFundManager) >> twoSampleAvailableFunds()
        expect:
        mockMvc
                .perform(get("/v1/available-funds"))

                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(2)));

    }

    List<Fund> twoSampleAvailableFunds() {
        [
                [:],[:]
        ]
    }

}
