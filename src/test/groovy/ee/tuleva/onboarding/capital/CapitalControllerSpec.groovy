package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import java.time.LocalDate

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.CAPITAL_PAYMENT
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.MEMBERSHIP_BONUS
import static ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventType.INVESTMENT_RETURN
import static ee.tuleva.onboarding.currency.Currency.EUR
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class CapitalControllerSpec extends BaseControllerSpec {

  private MockMvc mockMvc

  UserService userService = Mock(UserService)
  CapitalService capitalService = Mock(CapitalService)
  CapitalController controller = new CapitalController(userService, capitalService)
  User user = sampleUser().build()
  AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build()

  def setup() {
    mockMvc = mockMvcWithAuthenticationPrincipal(authenticatedPerson, controller)
  }

  def "Member capital statement"() {
    given:
    User user = sampleUser().build()
    1 * userService.getById(user.id) >> Optional.of(user)
    1 * capitalService.getCapitalRows(user.memberOrThrow.id) >> [
        new CapitalRow(MEMBERSHIP_BONUS, 100.0, 200.0, 100.0, 3.0, EUR),
        new CapitalRow(CAPITAL_PAYMENT, 300.0, 900.0, 400.0, 3.0, EUR),
    ]

    expect:
    mockMvc.perform(get("/v1/me/capital"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$[0].type', is(MEMBERSHIP_BONUS.name())))
        .andExpect(jsonPath('$[0].contributions', is(100.0.doubleValue())))
        .andExpect(jsonPath('$[0].profit', is(200.0.doubleValue())))
        .andExpect(jsonPath('$[0].value', is(300.0.doubleValue())))
        .andExpect(jsonPath('$[0].unitCount', is(100.0.doubleValue())))
        .andExpect(jsonPath('$[0].unitPrice', is(3.0.doubleValue())))
        .andExpect(jsonPath('$[0].currency', is(EUR.name())))

        .andExpect(jsonPath('$[1].type', is(CAPITAL_PAYMENT.name())))
        .andExpect(jsonPath('$[1].contributions', is(300.0.doubleValue())))
        .andExpect(jsonPath('$[1].profit', is(900.0.doubleValue())))
        .andExpect(jsonPath('$[1].value', is(1200.0.doubleValue())))
        .andExpect(jsonPath('$[1].unitCount', is(400.0.doubleValue())))
        .andExpect(jsonPath('$[1].unitPrice', is(3.0.doubleValue())))
        .andExpect(jsonPath('$[1].currency', is(EUR.name())))
  }


  def "Capital totals"() {
    given:
    1 * capitalService.getLatestAggregatedCapitalEvent() >> Optional.of(new AggregatedCapitalEvent(0,
        INVESTMENT_RETURN,
        new BigDecimal(1),
        new BigDecimal(1234.0),
        new BigDecimal(100.0),
        new BigDecimal(2.00),
        LocalDate.now()
    ))

    expect:
    mockMvc.perform(get("/v1/capital/total"))
        .andExpect(status().isOk())
        .andExpect(jsonPath('$.unitAmount', is(100)))
        .andExpect(jsonPath('$.totalValue', is(1234)))
        .andExpect(jsonPath('$.unitPrice', is(2)))
  }
}
