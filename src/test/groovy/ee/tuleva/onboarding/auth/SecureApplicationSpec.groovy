package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.user.UserController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(UserController.class)
class SecureApplicationSpec extends Specification {

    @Autowired
    MockMvc mvc

    def "/me endpoint works"() {
        expect:
        mvc.perform(get("/v1/me"))
                .andExpect(status().isUnauthorized())

    }

}
