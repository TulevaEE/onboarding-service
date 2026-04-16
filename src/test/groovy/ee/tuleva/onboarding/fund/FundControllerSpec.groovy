package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.mandate.MandateFixture
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import org.springframework.web.server.ResponseStatusException

import java.time.LocalDate
import java.util.stream.Collectors

import static org.springframework.http.HttpStatus.NOT_FOUND

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FundControllerSpec extends BaseControllerSpec {

    FundService fundService = Mock(FundService)
    FundController controller = new FundController(fundService)

    private MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "get: Get all funds"() {
        given:
        def language = "et"
        1 * fundService.getFunds(Optional.empty()) >> sampleFunds()
        expect:
        mockMvc
                .perform(get("/v1/funds").header("Accept-Language", language))

                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(sampleFunds().size())))
    }

    def "get: Get all funds defaults to et"() {
        given:
        def language = "et"
        1 * fundService.getFunds(Optional.empty()) >> sampleFunds()
        expect:
        mockMvc
            .perform(get("/v1/funds"))

            .andExpect(status().isOk())
            .andExpect(jsonPath('$', hasSize(sampleFunds().size())))
    }

    def "get: Get all funds by manager name"() {
        given:
        String fundManagerName = "Tuleva"
        def language = "et"
        Iterable<Fund> funds = sampleFunds().stream().filter( { f -> f.fundManager.name == fundManagerName}).collect(Collectors.toList())
        1 * fundService.getFunds(Optional.of(fundManagerName)) >> funds
        expect:
        mockMvc
                .perform(get("/v1/funds?fundManager.name=" + fundManagerName).header("Accept-Language", language))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(funds.size())))
                .andExpect(jsonPath('$[0].fundManager.name', is(fundManagerName)))
    }

    def "get: Get NAV history for fund"() {
        given:
        def isin = "EE0000003283"
        def navValues = [
            new NavValueResponse(LocalDate.of(2026, 2, 3), 1.0000G),
            new NavValueResponse(LocalDate.of(2026, 2, 4), 1.0012G),
        ]
        1 * fundService.getNavHistory(isin, null, null) >> navValues
        expect:
        mockMvc
                .perform(get("/v1/funds/${isin}/nav"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath('$', hasSize(2)))
                .andExpect(jsonPath('$[0].date', is("2026-02-03")))
                .andExpect(jsonPath('$[0].value', is(1.0d)))
                .andExpect(jsonPath('$[1].date', is("2026-02-04")))
                .andExpect(jsonPath('$[1].value', is(1.0012d)))
    }

    def "get: Get NAV history as CSV"() {
        given:
        def isin = "EE0000003283"
        def navValues = [
            new NavValueResponse(LocalDate.of(2026, 2, 3), 1.0000G),
            new NavValueResponse(LocalDate.of(2026, 2, 4), 1.0012G),
        ]
        1 * fundService.getNavHistory(isin, null, null) >> navValues
        expect:
        def result = mockMvc
                .perform(get("/v1/funds/${isin}/nav?format=csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", 'attachment; filename="nav-EE0000003283.csv"'))
                .andReturn()
        def csv = result.response.contentAsString
        csv.contains("Kuupäev;NAV (EUR)")
        csv.contains("03.02.2026;1.0000")
        csv.contains("04.02.2026;1.0012")
    }

    def "get: Get NAV history for unknown fund returns 404"() {
        given:
        1 * fundService.getNavHistory("UNKNOWN", null, null) >> { throw new ResponseStatusException(NOT_FOUND) }
        expect:
        mockMvc
                .perform(get("/v1/funds/UNKNOWN/nav"))
                .andExpect(status().isNotFound())
    }
}
