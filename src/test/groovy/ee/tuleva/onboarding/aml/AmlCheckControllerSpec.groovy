package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import org.springframework.http.MediaType

import static ee.tuleva.onboarding.aml.AmlCheckType.*
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AmlCheckControllerSpec extends BaseControllerSpec {

    AmlCheckService amlCheckService = Mock()

    AmlCheckController controller = new AmlCheckController(amlCheckService)

    def "GET /amlchecks gives all missing checks"() {
        given:
        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

        1 * amlCheckService.getMissingChecks(sampleAuthenticatedPerson) >> [RESIDENCY_MANUAL]
        expect:
        mvc.perform(get("/v1/amlchecks"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath('$.[0].type', is("RESIDENCY_MANUAL")))
            .andExpect(jsonPath('$.[0].success', is(false)))
    }

    def "POST /amlchecks adds new check if allowed"() {
        given:
        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
        def command = AmlCheckAddCommand.builder().type(POLITICALLY_EXPOSED_PERSON).success(true).build()
        1 * amlCheckService.addCheckIfMissing(sampleAuthenticatedPerson, command)
        expect:
        mvc.perform(post("/v1/amlchecks")
            .content("""{"type": "POLITICALLY_EXPOSED_PERSON", "success": true}""")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
    }

    def "POST /amlchecks accepts PEP check with success false when user declares they are a PEP"() {
        given:
        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
        def command = AmlCheckAddCommand.builder().type(POLITICALLY_EXPOSED_PERSON).success(false).build()
        1 * amlCheckService.addCheckIfMissing(sampleAuthenticatedPerson, command)
        expect:
        mvc.perform(post("/v1/amlchecks")
            .content("""{"type": "POLITICALLY_EXPOSED_PERSON", "success": false}""")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
    }

    def "POST /amlchecks fails is check type not allowed"() {
        given:
        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
        def command = AmlCheckAddCommand.builder().type(SK_NAME).success(true).build()
        0 * amlCheckService.addCheckIfMissing(sampleAuthenticatedPerson, command)
        expect:
        mvc.perform(post("/v1/amlchecks")
            .content("""{"type": "SK_NAME", "success": true}""")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError())
    }

    def "POST /amlchecks adds metadata"() {
        given:
        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
        def command = AmlCheckAddCommand.builder().type(OCCUPATION).success(true).metadata(["occupation": "asdfg"])
            .build()
        1 * amlCheckService.addCheckIfMissing(sampleAuthenticatedPerson, command)
        expect:
        mvc.perform(post("/v1/amlchecks")
            .content("""{"type": "OCCUPATION", "success": true, "metadata": { "occupation": "asdfg" }}""")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
    }

    AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
        .firstName("Erko")
        .lastName("Risthein")
        .personalCode("38501010002")
        .userId(2L)
        .build()
}
