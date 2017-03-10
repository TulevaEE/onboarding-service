package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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
