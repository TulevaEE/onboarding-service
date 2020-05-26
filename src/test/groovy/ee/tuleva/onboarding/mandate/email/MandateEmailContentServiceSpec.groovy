package ee.tuleva.onboarding.mandate.email

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification
import static java.util.Locale.ENGLISH

@SpringBootTest
class MandateEmailContentServiceSpec extends Specification {

    @Autowired
    MandateEmailContentService emailContentService

    def "get second pillar mandate email html content"() {
        given:
        when:
        String html = emailContentService.getSecondPillarHtml(ENGLISH)
        then:
        html.contains('tuleva@tuleva.ee')
    }

    def "get third pillar mandate email html content"() {
        given:
        when:
        String html = emailContentService.getThirdPillarHtml("test_account_1", ENGLISH)
        then:
        html.contains('tuleva@tuleva.ee')
        html.contains('test_account_1')
    }
}