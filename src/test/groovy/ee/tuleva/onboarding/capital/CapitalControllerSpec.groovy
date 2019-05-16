package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CapitalControllerSpec extends BaseControllerSpec {

    private MockMvc mockMvc

    UserService userService = Mock(UserService)
    CapitalService capitalService = Mock(CapitalService)
    CapitalController controller =
        new CapitalController(userService, capitalService)
    User user = sampleUser().build()
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build()

    def setup() {
        mockMvc = mockMvcWithAuthenticationPrincipal(authenticatedPerson, controller)
    }

    def "Member capital statement"() {
        given:
        CapitalStatement capitalStatement = CapitalStatementFixture.fixture().build()
        User user = UserFixture.sampleUser().build()
        1 * userService.getById(user.id) >> user
        1 * capitalService.getCapitalStatement(user.member.get().id) >>
            capitalStatement

        expect:
        mockMvc.perform(get("/v1/me/capital"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(MockMvcResultMatchers.jsonPath(
            '$.membershipBonus', is(capitalStatement.membershipBonus)))
                .andExpect(MockMvcResultMatchers.jsonPath(
            '$.capitalPayment', is(capitalStatement.capitalPayment)))
                .andExpect(MockMvcResultMatchers.jsonPath(
            '$.unvestedWorkCompensation', is(capitalStatement.unvestedWorkCompensation)))
                .andExpect(MockMvcResultMatchers.jsonPath(
            '$.workCompensation', is(capitalStatement.workCompensation)))
                .andExpect(MockMvcResultMatchers.jsonPath(
            '$.profit', is(capitalStatement.profit)))
    }

}
