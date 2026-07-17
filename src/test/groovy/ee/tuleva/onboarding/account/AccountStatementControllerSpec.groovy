package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.role.Role
import ee.tuleva.onboarding.fund.FundFixture
import ee.tuleva.onboarding.locale.LocaleConfiguration
import ee.tuleva.onboarding.locale.LocaleService
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.account.AccountStatementFixture.activeTuleva2ndPillarFundBalance
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON
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
  SavingsFundStatementService savingsFundStatementService = Mock(SavingsFundStatementService)
  LocaleService localeService = Mock(LocaleService)
  AccountStatementController controller = new AccountStatementController(accountStatementService, savingsFundStatementService, localeService)

  def "/pension-account-statement endpoint works"() {
    given:
    List<FundBalance> fundBalances = activeTuleva2ndPillarFundBalance
    1 * accountStatementService.getAccountStatement(_ as Person, _, _) >> fundBalances
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
                status              : fundBalances[0].fund.status.name(),
                inceptionDate       : fundBalances[0].fund.inceptionDate.toString(),
            ],
            value              : fundBalances[0].value.doubleValue(),
            unavailableValue   : fundBalances[0].unavailableValue.doubleValue(),
            currency           : fundBalances[0].currency,
            activeContributions: fundBalances[0].activeContributions,
            contributions      : fundBalances[0].contributions.doubleValue(),
            subtractions       : fundBalances[0].subtractions.doubleValue(),
            profit             : fundBalances[0].profit.doubleValue(),
            units              : fundBalances[0].units.doubleValue(),
        ])))
        .andExpect(jsonPath('$', hasSize(fundBalances.size())))
  }

  def "/pension-account-statement endpoint accepts language header and responds with appropriate fund.name"() {
    given:
    List<FundBalance> fundBalances = activeTuleva2ndPillarFundBalance
    1 * accountStatementService.getAccountStatement(_ as Person, _, _) >> fundBalances
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

  def "/pension-account-statement endpoint returns the statement when representing another party"() {
    given:
    AuthenticatedPerson representing = sampleAuthenticatedPersonAndMember()
        .role(new Role(PERSON, "61506150006", "Child Name"))
        .build()
    MockMvc mockMvcRepresenting = mockMvcWithAuthenticationPrincipal(representing, controller)
    List<FundBalance> fundBalances = activeTuleva2ndPillarFundBalance
    1 * accountStatementService.getAccountStatement(representing, null, null) >> fundBalances
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE

    when:
    def result = mockMvcRepresenting.perform(get("/v1/pension-account-statement"))

    then:
    result.andExpect(status().isOk())
        .andExpect(jsonPath('$', hasSize(fundBalances.size())))
        .andExpect(jsonPath('$[0].fund.isin', is(fundBalances[0].fund.isin)))
  }

  def "/pension-account-statement endpoint returns empty when acting under a company role"() {
    given:
    AuthenticatedPerson actingForCompany = sampleAuthenticatedPersonLegalEntity().build()
    MockMvc mockMvcCompany = mockMvcWithAuthenticationPrincipal(actingForCompany, controller)

    when:
    def result = mockMvcCompany.perform(get("/v1/pension-account-statement"))

    then:
    result.andExpect(status().isOk())
        .andExpect(jsonPath('$', hasSize(0)))
    0 * accountStatementService.getAccountStatement(_, _, _)
  }

  def "/savings-account-statement endpoint returns savings account balance when a statement exists"() {
    given:
    FundBalance fundBalance = FundBalance.builder()
        .fund(FundFixture.additionalSavingsFund())
        .currency("EUR")
        .units(10)
        .value(12)
        .contributions(10)
        .subtractions(0)
        .build()
    1 * savingsFundStatementService.getAccountStatement(_ as Person) >> Optional.of(fundBalance)
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE

    expect:
    mockMvc.perform(get("/v1/savings-account-statement"))
        .andExpect(status().isOk())
        .andExpect(jsonPath('fund.name', is(fundBalance.fund.nameEstonian)))
  }

  def "/savings-account-statement endpoint returns no content when there is no savings account"() {
    given:
    1 * savingsFundStatementService.getAccountStatement(_ as Person) >> Optional.empty()
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE

    expect:
    mockMvc.perform(get("/v1/savings-account-statement"))
        .andExpect(status().isNoContent())
  }

  def "/savings-account-statement endpoint propagates errors instead of swallowing them as no content"() {
    given:
    1 * savingsFundStatementService.getAccountStatement(_ as Person) >> { throw new IllegalStateException("boom") }
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE

    when:
    mockMvc.perform(get("/v1/savings-account-statement"))

    then:
    def exception = thrown(Exception)
    exception.cause instanceof IllegalStateException
  }
}
