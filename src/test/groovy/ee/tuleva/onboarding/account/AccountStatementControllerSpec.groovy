package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.locale.LocaleConfiguration
import ee.tuleva.onboarding.locale.LocaleService
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
  LocaleService localeService = Mock(LocaleService)
  AccountStatementController controller = new AccountStatementController(accountStatementService, localeService)

  def "/pension-account-statement endpoint works"() {
    given:
    List<FundBalance> fundBalances = activeTuleva2ndPillarFundBalance
    1 * accountStatementService.getAccountStatement(_ as Person) >> fundBalances
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE

    expect:
    mockMvc.perform(get("/v1/pension-account-statement"))
        .andExpect(status().isOk())
        .andExpect(jsonPath('$[0]', is([
            fund               : [
                fundManager         : [
                    name: fundBalances[0].fund.fundManager.name,
                ],
                isin                : fundBalances[0].fund.isin,
                name                : fundBalances[0].fund.nameEstonian,
                managementFeeRate   : fundBalances[0].fund.managementFeeRate.doubleValue(),
                pillar              : fundBalances[0].fund.pillar,
                ongoingChargesFigure: fundBalances[0].fund.ongoingChargesFigure.doubleValue(),
                status              : fundBalances[0].fund.status.name()
            ],
            value              : fundBalances[0].value.doubleValue(),
            unavailableValue   : fundBalances[0].unavailableValue.doubleValue(),
            currency           : fundBalances[0].currency,
            activeContributions: fundBalances[0].activeContributions,
            contributions      : fundBalances[0].contributions.doubleValue(),
            subtractions       : fundBalances[0].subtractions.doubleValue(),
            profit             : fundBalances[0].profit.doubleValue(),
        ])))
        .andExpect(jsonPath('$', hasSize(fundBalances.size())))
  }

  def "/pension-account-statement endpoint accepts language header and responds with appropriate fund.name"() {
    given:
    List<FundBalance> fundBalances = activeTuleva2ndPillarFundBalance
    1 * accountStatementService.getAccountStatement(_ as Person) >> fundBalances
    localeService.getCurrentLocale() >> Locale.forLanguageTag(language)

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
