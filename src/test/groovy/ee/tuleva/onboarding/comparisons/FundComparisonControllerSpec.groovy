package ee.tuleva.onboarding.comparisons

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult

import java.text.SimpleDateFormat
import java.time.Instant

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FundComparisonControllerSpec extends BaseControllerSpec {

    FundComparisonCalculatorService fundComparisonCalculatorService = Mock(FundComparisonCalculatorService)

    FundComparisonController controller = new FundComparisonController(fundComparisonCalculatorService)

    MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "fund comparison returns the calculation with a set time"() {
        given:
            ObjectMapper mapper = new ObjectMapper()
            1 * fundComparisonCalculatorService.calculateComparison(_ as Person, { verifyTimesClose(it, parseInstant("2006-01-01")) }, 2) >> sampleComparison()

        expect:
            MvcResult result = mockMvc.perform(get('/v1/fund-comparison')
                .param('from', '2006-01-01'))
                .andExpect(status().isOk())
                .andReturn()
            mapper.readValue(result.response.getContentAsString(), FundComparison) == sampleComparison()
    }

    def "fund comparison returns the calculation with a default time if not set"() {
        given:
            ObjectMapper mapper = new ObjectMapper()
            1 * fundComparisonCalculatorService.calculateComparison(_ as Person, { verifyTimesClose(it, parseInstant("2000-01-01")) }, 2) >> sampleComparison()

        expect:
            MvcResult result = mockMvc.perform(get('/v1/fund-comparison'))
                    .andExpect(status().isOk())
                    .andReturn()
            mapper.readValue(result.response.getContentAsString(), FundComparison) == sampleComparison()
    }

  def "validates dates too much in the past"() {
    when:
    mockMvc.perform(get('/v1/fund-comparison')
      .param('from', '1996-01-01'))
    then:
    thrown Exception
  }

    private static FundComparison sampleComparison() {
        return new FundComparison(0.05, 0.06, 0.07)
    }

    private static boolean verifyTimesClose(Instant time, Instant other) {
        return Math.abs(time.epochSecond - other.epochSecond) < 100
    }

    private static Instant parseInstant(String format) {
        return new SimpleDateFormat("yyyy-MM-dd").parse(format).toInstant()
    }
}
