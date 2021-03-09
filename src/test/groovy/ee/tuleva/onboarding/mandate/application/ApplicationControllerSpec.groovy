package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.*
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ApplicationControllerSpec extends BaseControllerSpec {
    ApplicationService applicationService = Mock()
    ApplicationController controller = new ApplicationController(applicationService)

    MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "/applications endpoint works"() {
        given:
        1 * applicationService.get(_ as Person) >> sampleApplications

        expect:
        mockMvc.perform(get('/v1/applications')
            .param('status', 'PENDING'))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.*', hasSize(1)))
            .andExpect(jsonPath('$[0].details.sourceFundIsin', is(sourceFundIsin)))
            .andExpect(jsonPath('$[0].details.targetFundIsin', is(targetFundIsin)))
    }

    def "can cancel applications"() {
        def mandate = sampleMandate()
        def applicationId = 123L
        1 * applicationService.createCancellationMandate(_ as Person, _ as Long, applicationId) >>
            new ApplicationCancellationResponse(mandate.id)

        expect:
        mockMvc.perform(post("/v1/applications/$applicationId/cancellations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.mandateId', is(mandate.id.intValue())))
    }

    String sourceFundIsin = "EE123"
    String targetFundIsin = "EE234"

    List<Application> sampleApplications = [
        Application.builder()
            .status(FAILED)
            .build(),
        Application.builder()
            .status(COMPLETE)
            .build(),
        Application.builder()
            .status(PENDING)
            .details(TransferApplicationDetails.builder()
                .sourceFundIsin(sourceFundIsin)
                .targetFundIsin(targetFundIsin)
                .build())
            .build()
    ]
}
