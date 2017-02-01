package ee.tuleva.onboarding.comparisons

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ComparisonControllerSpec extends Specification {

    def comparisonService = Mock(ComparisonService)

    def controller = new ComparisonController(comparisonService)

    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build()

    String LHVinterestIsin = "EE3600019816";

    def "comparison endpoint works" (){
        when:
        comparisonService.comparedResults(_) >> new Comparison(LHVinterestIsin, 123.0f)
        then:
        mvc.perform(get("/v1/comparisons/?totalCapital=1000&age=30&monthlyWage=2000&isin="+LHVinterestIsin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.currency',is("EUR"))).andExpect(jsonPath('$.isin', is(LHVinterestIsin)))

    }

}