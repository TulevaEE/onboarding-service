package ee.tuleva

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

@SpringBootTest
class OnboardingServiceApplicationSpec extends Specification {

    @Autowired
    WebApplicationContext context

    def "context loads"() {
        expect:
        context != null
    }
}