package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.CAPITAL_PAYMENT
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.MEMBERSHIP_BONUS
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
    1 * userService.getById(user.id) >> user
    1 * capitalService.getCapitalRows(user.memberOrThrow.id) >> [
        new CapitalRow(MEMBERSHIP_BONUS, 100.0, 200.0, EUR),
        new CapitalRow(CAPITAL_PAYMENT, 300.0, 400.0, EUR),
    ]

    expect:
    mockMvc.perform(get("/v1/me/capital"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$[0].type', is(MEMBERSHIP_BONUS.name())))
        .andExpect(jsonPath('$[0].contributions', is(100.0.doubleValue())))
        .andExpect(jsonPath('$[0].profit', is(200.0.doubleValue())))
        .andExpect(jsonPath('$[0].value', is(300.0.doubleValue())))
        .andExpect(jsonPath('$[0].currency', is(EUR.name())))

        .andExpect(jsonPath('$[1].type', is(CAPITAL_PAYMENT.name())))
        .andExpect(jsonPath('$[1].contributions', is(300.0.doubleValue())))
        .andExpect(jsonPath('$[1].profit', is(400.0.doubleValue())))
        .andExpect(jsonPath('$[1].value', is(700.0.doubleValue())))
        .andExpect(jsonPath('$[1].currency', is(EUR.name())))
  }
}
