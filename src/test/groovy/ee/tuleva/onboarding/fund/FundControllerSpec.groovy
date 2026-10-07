package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics
import ee.tuleva.onboarding.mandate.MandateFixture
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.stream.Collectors

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00
import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FundControllerSpec extends BaseControllerSpec {

    FundService fundService = Mock(FundService)
    SavingsFundUnitStats ledgerUnits = Stub(SavingsFundUnitStats)
    FundManagerUnitsInRegister registerCounts = Stub(FundManagerUnitsInRegister)
    FundManagerUnits fundManagerUnits = new FundManagerUnits(ledgerUnits, registerCounts, Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC), "10000000")
    FundController controller = new FundController(fundService, PublishedManagementFeeFixture.withNoRateInForce(), fundManagerUnits)

    private MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "get: Get all funds"() {
        given:
        def language = "et"
        1 * fundService.getFunds(Optional.empty()) >> responsesFor(sampleFunds())
        expect:
        mockMvc
                .perform(get("/v1/funds").header("Accept-Language", language))

                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(sampleFunds().size())))
    }

    def "get: Get all funds defaults to et"() {
        given:
        def language = "et"
        1 * fundService.getFunds(Optional.empty()) >> responsesFor(sampleFunds())
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
        1 * fundService.getFunds(Optional.of(fundManagerName)) >> responsesFor(funds)
        expect:
        mockMvc
                .perform(get("/v1/funds?fundManager.name=" + fundManagerName).header("Accept-Language", language))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(funds.size())))
                .andExpect(jsonPath('$[0].fundManager.name', is(fundManagerName)))
    }

    def "get: a Tuleva fund shows the management fee rate in force"() {
        given:
        def controllerWithRates = new FundController(fundService,
            PublishedManagementFeeFixture.withRateInForce(TUK00, 0.00163), fundManagerUnits)
        1 * fundService.getFunds(Optional.empty()) >> responsesFor(sampleFunds())
        expect:
        mockMvc(controllerWithRates)
            .perform(get("/v1/funds"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$[?(@.isin == "EE3600109443")].managementFeeRate', contains(0.00163d)))
    }

    def "get: a Tuleva pension fund shows the fund manager's units at the last month end"() {
        given:
        registerCounts.fundManagerUnitsOn("EE3600109443", LocalDate.parse("2026-09-30")) >> Optional.of(306250.0)
        1 * fundService.getFunds(Optional.empty()) >> responsesFor(sampleFunds())
        expect:
        mockMvc
            .perform(get("/v1/funds"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$[?(@.isin == "EE3600109443")].fundManagerUnits', contains(306250.0d)))
            .andExpect(jsonPath('$[?(@.isin == "EE3600109443")].fundManagerUnitsDate', contains("2026-09-30")))
    }

    private static List<ExtendedApiFundResponse> responsesFor(Iterable<Fund> funds) {
        funds.collect { new ExtendedApiFundResponse(it, PensionFundStatistics.getNull(), Locale.ENGLISH) }
    }
}
