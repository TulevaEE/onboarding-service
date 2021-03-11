package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.FAILED
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.transferApplication
import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ApplicationControllerSpec extends BaseControllerSpec {
  ApplicationService applicationService = Mock()
  ApplicationCancellationService applicationCancellationService = Mock()
  ApplicationController controller = new ApplicationController(applicationCancellationService,applicationService)

  MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def "/applications endpoint works"() {
    given:
    1 * applicationService.getApplications(_ as Person) >> sampleApplications

    expect:
    mockMvc.perform(get('/v1/applications')
      .param('status', 'PENDING'))
      .andExpect(status().isOk())
      .andExpect(jsonPath('$.*', hasSize(1)))
      .andExpect(jsonPath('$[0].type', is("TRANSFER")))
      .andExpect(jsonPath('$[0].details.sourceFund.isin', is(sourceFundIsin)))
      .andExpect(jsonPath('$[0].details.exchanges[0].targetFund.isin', is(targetFundIsin)))
      .andExpect(jsonPath('$[0].details.exchanges[0].amount', is(1)))
  }

  def "can cancel applications"() {
    def mandate = sampleMandate()
    def applicationId = 123L
    1 * applicationCancellationService.createCancellationMandate(_ as Person, _ as Long, applicationId) >>
      new ApplicationCancellationResponse(mandate.id)

    expect:
    mockMvc.perform(post("/v1/applications/$applicationId/cancellations"))
      .andExpect(status().isOk())
      .andExpect(jsonPath('$.mandateId', is(mandate.id.intValue())))
  }

  String sourceFundIsin = "AE123232334"
  String targetFundIsin = "EE3600109443"

  List<Application> sampleApplications = [
    Application.builder()
      .status(FAILED)
      .build(),
    Application.builder()
      .status(COMPLETE)
      .build(),
    transferApplication().build()
  ]
}
