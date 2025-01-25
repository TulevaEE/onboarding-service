package ee.tuleva.onboarding

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

@SpringBootTest
@ActiveProfiles("dev")
class OnboardingServiceApplicationSpec extends Specification {

    @Autowired
    WebApplicationContext context

    def "context loads"() {
        expect:
        context != null
    }

}
