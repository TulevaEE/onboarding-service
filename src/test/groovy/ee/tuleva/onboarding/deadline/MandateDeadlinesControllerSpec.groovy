package ee.tuleva.onboarding.deadline

import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.test.web.servlet.MockMvc

import java.time.Clock
import java.time.Instant

import static java.time.ZoneOffset.UTC
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class MandateDeadlinesControllerSpec extends BaseControllerSpec {
  Clock clock = Clock.fixed(Instant.parse("2021-03-11T10:00:00Z"), UTC)
  PublicHolidays publicHolidays = new PublicHolidays(clock)

  MandateDeadlinesController controller = new MandateDeadlinesController(clock, publicHolidays)

  MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def "can get mandate deadlines"() {
    expect:
    mockMvc.perform(get('/v1/mandate-deadlines'))
      .andExpect(status().isOk())
      .andExpect(jsonPath('$.periodEnding', is("2021-03-31")))
      .andExpect(jsonPath('$.transferMandateCancellationDeadline', is("2021-03-31")))
      .andExpect(jsonPath('$.transferMandateFulfillmentDate', is("2021-05-03")))
      .andExpect(jsonPath('$.earlyWithdrawalCancellationDeadline', is("2021-07-31")))
      .andExpect(jsonPath('$.earlyWithdrawalFulfillmentDate', is("2021-09-01")))
      .andExpect(jsonPath('$.withdrawalCancellationDeadline', is("2021-03-31")))
      .andExpect(jsonPath('$.withdrawalFulfillmentDate', is("2021-04-16")))
  }
}