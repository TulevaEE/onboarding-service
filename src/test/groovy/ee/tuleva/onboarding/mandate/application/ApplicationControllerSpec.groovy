package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.transferApplication
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.transferApplicationDetails
import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ApplicationControllerSpec extends BaseControllerSpec {
  ApplicationService applicationService = Mock()
  ApplicationCancellationService applicationCancellationService = Mock()
  ApplicationController controller = new ApplicationController(applicationCancellationService, applicationService)

  MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def "can get a single application"() {
    given:
    def application = transferApplication().build()
    def details = transferApplicationDetails().build()
    1 * applicationService.getApplication(application.id, _ as Person) >> application

    expect:
    mockMvc.perform(get("/v1/applications/${application.id}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath('$.type', is(application.type.name())))
      .andExpect(jsonPath('$.details.sourceFund.isin', is(details.sourceFund.isin)))
      .andExpect(jsonPath('$.details.exchanges[0].targetFund.isin', is(details.exchanges[0].targetFund.isin)))
      .andExpect(jsonPath('$.details.exchanges[0].amount', is(details.exchanges[0].amount.doubleValue())))
      .andDo(print())
  }

  def "can get all pending applications"() {
    given:
    1 * applicationService.getApplications(PENDING, _ as Person) >> [transferApplication().build()]

    expect:
    mockMvc.perform(get('/v1/applications')
      .param('status', 'PENDING'))
      .andExpect(status().isOk())
      .andExpect(jsonPath('$.*', hasSize(1)))
      .andExpect(jsonPath('$[0].type', is("TRANSFER")))
      .andExpect(jsonPath('$[0].details.sourceFund.isin', is(sourceFundIsin)))
      .andExpect(jsonPath('$[0].details.exchanges[0].targetFund.isin', is(targetFundIsin)))
      .andExpect(jsonPath('$[0].details.exchanges[0].amount', is(1.0d)))
      .andDo(print())
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
}
