package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers

import static ee.tuleva.onboarding.account.AccountStatementFixture.sampleConvertedFundBalanceWithActiveTulevaFund
import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
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
        List<FundBalance> fundBalances = []
        1 * accountStatementService.getAccountStatement(_ as Person) >> fundBalances
        expect:
        mockMvc.perform(get("/v1/pension-account-statement"))
            .andExpect(status().isOk())
    }

    def "/pension-account-statement endpoint accepts language header and responds with appropriate fund.name"() {
        given:
        List<FundBalance> fundBalances = sampleConvertedFundBalanceWithActiveTulevaFund

        1 * accountStatementService.getAccountStatement(_ as Person) >> fundBalances

        expect:
        mockMvc.perform(get("/v1/pension-account-statement")
            .header("Accept-Language", language)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(jsonPath('$', hasSize(fundBalances.size())))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$', hasSize(fundBalances.size())))
            .andExpect(jsonPath('$[0].fund.name', is(translation)))
            .andExpect(jsonPath('$', hasSize(fundBalances.size())))
        where:
        language | translation
        'null'   | "Tuleva maailma aktsiate pensionifond"
        "et"     | "Tuleva maailma aktsiate pensionifond"
        "en"     | "Tuleva world stock pensionfund"

    }
}
