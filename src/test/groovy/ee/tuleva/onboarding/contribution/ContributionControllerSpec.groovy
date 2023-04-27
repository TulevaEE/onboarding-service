package ee.tuleva.onboarding.contribution

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.epis.EpisService

import java.time.Instant

import static ee.tuleva.onboarding.currency.Currency.EUR
import static org.hamcrest.Matchers.is
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ContributionControllerSpec extends BaseControllerSpec {

  EpisService episService = Mock(EpisService)
  ContributionController controller = new ContributionController(episService)

  def "getting contributions work"() {
    given:
    def mvc = mockMvc(controller)
    1 * episService.getContributions(_) >> [
        new Contribution(
            Instant.parse("2023-04-26T10:00:00Z"),
            "Tuleva Fondid AS",
            12.34,
            EUR,
            2
        )
    ]

    expect:
    mvc.perform(get("/v1/contributions"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath('$.[0].time', is("2023-04-26T10:00:00Z")))
        .andExpect(jsonPath('$.[0].sender', is("Tuleva Fondid AS")))
        .andExpect(jsonPath('$.[0].amount', is(12.34d)))
        .andExpect(jsonPath('$.[0].currency', is("EUR")))
        .andExpect(jsonPath('$.[0].pillar', is(2)))
  }
}
