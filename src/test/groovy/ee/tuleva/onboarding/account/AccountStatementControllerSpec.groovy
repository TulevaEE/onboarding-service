package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.user.User
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AccountStatementControllerSpec extends BaseControllerSpec {

    MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    AccountStatementService accountStatementService = Mock(AccountStatementService)
    AccountStatementController controller = new AccountStatementController(accountStatementService)

    def "/pension-account-statement endpoint works"() {
        given:
        UUID statisticsIdentifier = UUID.randomUUID()
        1 * accountStatementService.getMyPensionAccountStatement(_ as User, statisticsIdentifier) >> []

        expect:
            mockMvc.perform(get("/v1/pension-account-statement").header("x-statistics-identifier", statisticsIdentifier))
                .andExpect(status().isOk())
    }

}
