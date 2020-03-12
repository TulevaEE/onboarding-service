package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.account.AccountStatementFixture.activeTuleva2ndPillarFundBalance
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
        List<FundBalance> fundBalances = activeTuleva2ndPillarFundBalance
        1 * accountStatementService.getAccountStatement(_ as Person) >> fundBalances

        expect:
        mockMvc.perform(get("/v1/pension-account-statement"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$[0].value', is(fundBalances[0].value.doubleValue())))
            .andExpect(jsonPath('$[0].unavailableValue', is(fundBalances[0].unavailableValue.doubleValue())))
            .andExpect(jsonPath('$[0].activeContributions', is(fundBalances[0].activeContributions)))
            .andExpect(jsonPath('$[0].currency', is(fundBalances[0].currency)))
            .andExpect(jsonPath('$[0].contributions', is(fundBalances[0].contributions.doubleValue())))
            .andExpect(jsonPath('$[0].subtractions', is(fundBalances[0].subtractions.doubleValue())))
            .andExpect(jsonPath('$[0].contributionSum', is(fundBalances[0].contributionSum.doubleValue())))
            .andExpect(jsonPath('$[0].profit', is(fundBalances[0].profit.doubleValue())))
            .andExpect(jsonPath('$[0].pillar', is(fundBalances[0].pillar)))
            .andExpect(jsonPath('$[0].fund.isin', is(fundBalances[0].fund.isin)))
            .andExpect(jsonPath('$[0].fund.name', is(fundBalances[0].fund.nameEstonian)))
            .andExpect(jsonPath('$[0].fund.pillar', is(fundBalances[0].fund.pillar)))
            .andExpect(jsonPath('$[0].fund.fundManager.name', is(fundBalances[0].fund.fundManager.name)))
            .andExpect(jsonPath('$', hasSize(fundBalances.size())))
    }

    def "/pension-account-statement endpoint accepts language header and responds with appropriate fund.name"() {
        given:
        List<FundBalance> fundBalances = activeTuleva2ndPillarFundBalance
        1 * accountStatementService.getAccountStatement(_ as Person) >> fundBalances

        expect:
        mockMvc.perform(get("/v1/pension-account-statement")
            .header("Accept-Language", language)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath('$[0].fund.name', is(translation)))
            .andExpect(jsonPath('$', hasSize(fundBalances.size())))
        where:
        language | translation
        'null'   | "Tuleva maailma aktsiate pensionifond"
        "et"     | "Tuleva maailma aktsiate pensionifond"
        "en"     | "Tuleva world stock pensionfund"

    }
}
