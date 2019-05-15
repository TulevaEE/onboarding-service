package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import org.springframework.http.MediaType

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AmlCheckControllerSpec extends BaseControllerSpec {

    AmlCheckService amlCheckService = Mock()

    AmlCheckController controller = new AmlCheckController(amlCheckService)

    def "GET /amlchecks gives all missing check types"() {
        given:
        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

        1 * amlCheckService.getMissingChecks(sampleAuthenticatedPerson.userId) >> [AmlCheckType.RESIDENCY_MANUAL]
        expect:
        mvc.perform(get("/v1/amlchecks"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.[0]', is("RESIDENCY_MANUAL")))
    }

    def "POST /amlchecks adds new check if allowed"() {
        given:
        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
        1 * amlCheckService.addCheckIfMissing(sampleAuthenticatedPerson.userId, AmlCheckType.POLITICALLY_EXPOSED_PERSON, true)
        expect:
        mvc.perform(post("/v1/amlchecks")
            .content("""{"type": "POLITICALLY_EXPOSED_PERSON", "success": true}""")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
    }

    def "POST /amlchecks fails is check type not allowed"() {
        given:
        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
        0 * amlCheckService.addCheckIfMissing(sampleAuthenticatedPerson.userId, AmlCheckType.SK_NAME, true)
        expect:
        mvc.perform(post("/v1/amlchecks")
            .content("""{"type": "SK_NAME", "success": true}""")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError())
    }

    AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
        .firstName("Erko")
        .lastName("Risthein")
        .personalCode("38501010002")
        .userId(2L)
        .build()
}
