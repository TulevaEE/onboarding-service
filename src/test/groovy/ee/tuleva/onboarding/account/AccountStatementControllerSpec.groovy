package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.account.FundBalance
import ee.tuleva.onboarding.mandate.statistics.FundTransferStatisticsService
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AccountStatementControllerSpec extends BaseControllerSpec {

    MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    EpisService episService = Mock(EpisService)
    FundTransferStatisticsService fundTransferStatisticsService = Mock(FundTransferStatisticsService)
    AccountStatementController controller =
            new AccountStatementController(episService, fundTransferStatisticsService)

    def "/pension-account-statement endpoint works"() {
        given:
        List<FundBalance> fundBalances = []
        UUID statisticsIdentifier = UUID.randomUUID()
        1 * episService.getAccountStatement(_ as Person) >> fundBalances
        1 * fundTransferStatisticsService.saveFundValueStatistics(fundBalances, statisticsIdentifier)
        expect:
            mockMvc.perform(get("/v1/pension-account-statement").header("x-statistics-identifier", statisticsIdentifier))
                .andExpect(status().isOk())
    }

}
