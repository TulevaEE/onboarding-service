package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.user.User
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.capital.InitialCapitalFixture.initialCapitalFixture
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class InitialCapitalControllerSpec extends BaseControllerSpec {

    private MockMvc mockMvc

    InitialCapitalRepository initialCapitalRepository = Mock(InitialCapitalRepository)
    InitialCapitalController controller = new InitialCapitalController(initialCapitalRepository)
    User user = sampleUser().build()
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build()

    def setup() {
        mockMvc = mockMvcWithAuthenticationPrincipal(authenticatedPerson, controller)
    }

    def "InitialCapital: Get information about current user initial capital"() {
        given:
        def initialCapital = initialCapitalFixture(user).build()
        1 * initialCapitalRepository.findByUserId(user.id) >> initialCapital

        expect:
        mockMvc.perform(get("/v1/me/initial-capital"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.amount', is(initialCapital.amount.toDouble())))
                .andExpect(jsonPath('$.currency', is(initialCapital.currency)))
                .andExpect(jsonPath('$.ownershipFraction', is(initialCapital.ownershipFraction.toDouble())))
    }

}
