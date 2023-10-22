package ee.tuleva.onboarding.comparisons.returns

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.http.MediaType

import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.*
import static ee.tuleva.onboarding.comparisons.returns.ReturnsController.BEGINNING_OF_TIMES
import static ee.tuleva.onboarding.currency.Currency.EUR
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ReturnsControllerSpec extends BaseControllerSpec {

    def returnsService = Mock(ReturnsService)

    def controller = new ReturnsController(returnsService)

    def mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "can GET /returns"() {
        given:
        def fromDate = "2017-01-01"
        def type = FUND
        def key = "EE123"
        BigDecimal rate = 1.0
        BigDecimal amount = 30.03
        def aReturn = Return.builder()
            .key(key)
            .type(type)
            .rate(rate)
            .amount(amount)
            .currency(EUR)
            .from(LocalDate.parse(fromDate))
            .build()
        def returns = Returns.builder()
            .returns([aReturn])
            .build()
        returnsService.get(_ as Person, LocalDate.parse(fromDate), null) >> returns

        expect:
        mockMvc.perform(get("/v1/returns")
            .param("from", fromDate))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath('$.from', is(fromDate)))
            .andExpect(jsonPath('$.returns[0].type', is(type.toString())))
            .andExpect(jsonPath('$.returns[0].key', is(key)))
            .andExpect(jsonPath('$.returns[0].rate', is(rate.toDouble())))
            .andExpect(jsonPath('$.returns[0].amount', is(amount.toDouble())))
            .andExpect(jsonPath('$.returns[0].currency', is(EUR.name())))
    }

    def "can GET /returns without specifying fromDate"() {
        given:
        def type = FUND
        def key = "EE123"
        def rate = 1.0
        def amount = 30.03
        def aReturn = Return.builder().key(key).type(type).rate(rate).amount(amount).currency(EUR).from(BEGINNING_OF_TIMES).build()
        def returns = Returns.builder()
            .returns([aReturn])
            .build()
        returnsService.get(_ as Person, BEGINNING_OF_TIMES, null) >> returns

        expect:
        mockMvc.perform(get("/v1/returns"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath('$.from', is(BEGINNING_OF_TIMES.toString())))
            .andExpect(jsonPath('$.returns[0].type', is(type.toString())))
            .andExpect(jsonPath('$.returns[0].key', is(key)))
            .andExpect(jsonPath('$.returns[0].rate', is(rate.toDouble())))
            .andExpect(jsonPath('$.returns[0].amount', is(amount.toDouble())))
            .andExpect(jsonPath('$.returns[0].currency', is(EUR.name())))
    }

    def "can GET /returns by specifying keys"() {
        given:
        def fromDate = "2017-01-01"
        def key1 = "SECOND_PILLAR"
        def key2 = "EE123"
        def key3 = "EPI"
        def rate = 1.0
        def amount = 30.03
        def returns = Returns.builder()
            .returns([
                Return.builder().key(key1).type(PERSONAL).rate(rate).amount(amount).currency(EUR).from(LocalDate.parse(fromDate)).build(),
                Return.builder().key(key2).type(FUND).rate(rate).amount(amount).currency(EUR).from(LocalDate.parse(fromDate)).build(),
                Return.builder().key(key3).type(INDEX).rate(rate).amount(amount).currency(EUR).from(LocalDate.parse(fromDate)).build(),
            ])
            .build()
        returnsService.get(_ as Person, LocalDate.parse(fromDate), [key1, key2, key3]) >> returns

        expect:
        mockMvc.perform(get("/v1/returns")
            .param("from", fromDate)
            .param("keys[]", key1)
            .param("keys[]", key2)
            .param("keys[]", key3))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath('$.from', is(fromDate)))

            .andExpect(jsonPath('$.returns[0].type', is(PERSONAL.toString())))
            .andExpect(jsonPath('$.returns[0].key', is(key1)))
            .andExpect(jsonPath('$.returns[0].rate', is(rate.toDouble())))
            .andExpect(jsonPath('$.returns[0].amount', is(amount.toDouble())))
            .andExpect(jsonPath('$.returns[0].currency', is(EUR.name())))

            .andExpect(jsonPath('$.returns[1].type', is(FUND.toString())))
            .andExpect(jsonPath('$.returns[1].key', is(key2)))
            .andExpect(jsonPath('$.returns[1].rate', is(rate.toDouble())))
            .andExpect(jsonPath('$.returns[1].amount', is(amount.toDouble())))
            .andExpect(jsonPath('$.returns[1].currency', is(EUR.name())))

            .andExpect(jsonPath('$.returns[2].type', is(INDEX.toString())))
            .andExpect(jsonPath('$.returns[2].key', is(key3)))
            .andExpect(jsonPath('$.returns[2].rate', is(rate.toDouble())))
            .andExpect(jsonPath('$.returns[2].amount', is(amount.toDouble())))
            .andExpect(jsonPath('$.returns[2].currency', is(EUR.name())))
    }

}
