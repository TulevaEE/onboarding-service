package ee.tuleva.comparisons

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(ComparisonController.class)
@WithMockUser
class ComparisonControllerSpec extends Specification {

    @Autowired
    MockMvc mvc

    String LHVinterestIsin = "EE3600019816";

    @Autowired
    ComparisonService comparisonService;

    def "comparison works"() {
        expect:
        mvc.perform(get("/comparisons/?totalCapital=1000&age=30&monthlyWage=2000&isin="+LHVinterestIsin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .andExpect(jsonPath('$isin', is (LHVinterestIsin))
        ))
    }

}