package ee.tuleva.onboarding.account

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType
import ee.eesti.xtee6.kpr.PersonalSelectionResponseType
import ee.tuleva.domain.fund.Fund
import ee.tuleva.domain.fund.FundRepository
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.kpr.KPRClient
import ee.tuleva.onboarding.user.User
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AccountStatementControllerSpec extends BaseControllerSpec {

    MockMvc mockMvc

    def setup() {
        mockMvc = getMockMvc(controller)
    }

    AccountStatementService accountStatementService = Mock(AccountStatementService)
    AccountStatementController controller = new AccountStatementController(accountStatementService)

    def "/pension-account-statement endpoint works"() {
        given:
        1 * accountStatementService.getMyPensionAccountStatement(_) >> []

        expect:
            mockMvc.perform(get("/v1/pension-account-statement"))
                .andExpect(status().isOk())
    }

}
