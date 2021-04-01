package ee.tuleva.onboarding.deadline

import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.deadline.MandateDeadlinesFixture.sampleDeadlines
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print

class MandateDeadlinesControllerSpec extends BaseControllerSpec {
  MandateDeadlinesService mandateDeadlinesService = Mock()
  MandateDeadlinesController controller = new MandateDeadlinesController(mandateDeadlinesService)

  MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def "can get mandate deadlines"() {
    given:
    mandateDeadlinesService.deadlines >> sampleDeadlines()
    expect:
    mockMvc.perform(get('/v1/mandate-deadlines'))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath('$.periodEnding', is("2021-03-31T23:59:59.999999999Z")))
      .andExpect(jsonPath('$.transferMandateCancellationDeadline', is("2021-03-31T23:59:59.999999999Z")))
      .andExpect(jsonPath('$.transferMandateFulfillmentDate', is("2021-05-03")))
      .andExpect(jsonPath('$.earlyWithdrawalCancellationDeadline', is("2021-07-31T23:59:59.999999999Z")))
      .andExpect(jsonPath('$.earlyWithdrawalFulfillmentDate', is("2021-09-01")))
      .andExpect(jsonPath('$.withdrawalCancellationDeadline', is("2021-03-31T23:59:59.999999999Z")))
      .andExpect(jsonPath('$.withdrawalFulfillmentDate', is("2021-04-16")))
  }
}