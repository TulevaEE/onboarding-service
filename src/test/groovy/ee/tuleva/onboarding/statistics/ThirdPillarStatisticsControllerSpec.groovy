package ee.tuleva.onboarding.statistics

import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.http.MediaType

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.statistics.ThirdPillarStatisticsFixture.sampleThirdPillarStatistics
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ThirdPillarStatisticsControllerSpec extends BaseControllerSpec {

    ThirdPillarStatisticsRepository repository = Mock()

    ThirdPillarStatisticsController controller = new ThirdPillarStatisticsController(repository)

    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()

    def mvc = mockMvcWithAuthenticationPrincipal(authenticatedPerson, controller)

    def "POST /statistics saves to the database"() {
        given:
        def statistics = sampleThirdPillarStatistics().mandateId(765).build()
        repository.save(statistics) >> statistics

        expect:
        mvc.perform(post("/v1/statistics")
            .content(mapper.writeValueAsString(statistics))
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.mandateId', is(statistics.mandateId.intValue())))
            .andExpect(jsonPath('$.recurringPayment', is(statistics.recurringPayment?.doubleValue())))
            .andExpect(jsonPath('$.singlePayment', is(statistics.singlePayment?.doubleValue())))
    }
}