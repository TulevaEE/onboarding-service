package ee.tuleva.onboarding.account

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(AccountStatementController.class)
@AutoConfigureMockMvc
@WithMockUser
class AccountStatementControllerSpec extends Specification {

    @Autowired
    MockMvc mvc

    def "/pension-account-statement endpoint works"() {
        expect:
        mvc.perform(get("/v1/pension-account-statement"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$[1].isin', is("EE3600019808")))
    }

}
