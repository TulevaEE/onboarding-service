package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.response.FundDto
import org.hamcrest.Matchers
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
        1 * applicationService.get(_ as Person, 'et') >> sampleApplicationList

        expect:
        mockMvc.perform(get('/v1/applications')
            .header('Accept-Language', 'et')
            .param('status', 'PENDING'))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.*', hasSize(1)))
            .andExpect(jsonPath('$[0].details.sourceFund.name', is('source fund name est')))
            .andExpect(jsonPath('$[0].details.targetFund.name', is('target fund name est')))
    }

    FundDto sourceFund = new FundDto(Fund.builder()
        .nameEstonian("source fund name est")
        .nameEnglish("source fund name eng")
        .build(), "et")

    FundDto targetFund = new FundDto(Fund.builder()
        .nameEstonian("target fund name est")
        .nameEnglish("target fund name eng")
        .build(), "et")

    List<Application> sampleApplicationList = [
        Application.builder()
            .status(ApplicationStatus.FAILED)
            .build(),
        Application.builder()
            .status(ApplicationStatus.COMPLETE)
            .build(),
        Application.builder()
            .status(ApplicationStatus.PENDING)
            .details(TransferApplicationDetails.builder()
                .sourceFund(sourceFund)
                .targetFund(targetFund)
                .build())
            .build()
    ]
}
