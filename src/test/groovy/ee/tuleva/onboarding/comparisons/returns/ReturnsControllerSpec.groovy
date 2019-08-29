package ee.tuleva.onboarding.comparisons.returns

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.http.MediaType

import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND
import static ee.tuleva.onboarding.comparisons.returns.ReturnsController.DEFAULT_DATE
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
        def value = 1.0
        def returns = Returns.builder()
            .from(LocalDate.parse(fromDate))
            .returns([Return.builder().key(key).type(type).value(value).build()])
            .build()
        returnsService.get(_ as Person, LocalDate.parse(fromDate)) >> returns

        expect:
        mockMvc.perform(get("/v1/returns")
            .param("from", fromDate))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.from', is(fromDate)))
            .andExpect(jsonPath('$.returns[0].type', is(type.toString())))
            .andExpect(jsonPath('$.returns[0].key', is(key)))
            .andExpect(jsonPath('$.returns[0].value', is(value.toDouble())))
    }

    def "can GET /return without specifying fromDate"() {
        given:
        def type = FUND
        def key = "EE123"
        def value = 1.0
        def returns = Returns.builder()
            .from(DEFAULT_DATE)
            .returns([Return.builder().key(key).type(type).value(value).build()])
            .build()
        returnsService.get(_ as Person, DEFAULT_DATE) >> returns

        expect:
        mockMvc.perform(get("/v1/returns"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.from', is(DEFAULT_DATE.toString())))
            .andExpect(jsonPath('$.returns[0].type', is(type.toString())))
            .andExpect(jsonPath('$.returns[0].key', is(key)))
            .andExpect(jsonPath('$.returns[0].value', is(value.toDouble())))
    }
}
