package ee.tuleva.onboarding.mandate.email

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification
import static java.util.Locale.ENGLISH

@SpringBootTest
class MandateEmailContentServiceSpec extends Specification {

    @Autowired
    MandateEmailContentService emailContentService

    def "second pillar shows message to convert when third pillar not in tuleva"() {
        given:
        def isFullyConverted = false
        when:
        String html = emailContentService.getSecondPillarHtml(isFullyConverted, ENGLISH)
        then:
        html.contains('tuleva@tuleva.ee')
        html.contains('Open third pillar here:')
    }

    def "third pillar shows message to convert when second pillar not in tuleva"() {
        given:
        def isFullyConverted = false
        when:
        String html = emailContentService.getThirdPillarHtml("test_account_1", isFullyConverted, ENGLISH)
        then:
        html.contains('tuleva@tuleva.ee')
        html.contains('test_account_1')
        html.contains('Open second pillar here:')
    }

    def "second pillar shows no extra message when third pillar is fully converted"() {
        given:
        def isFullyConverted = true
        when:
        String html = emailContentService.getSecondPillarHtml(isFullyConverted, ENGLISH)
        then:
        html.contains('tuleva@tuleva.ee')
        !html.contains('Open third pillar here:')
    }

    def "third pillar shows no extra message when second pillar is fully converted"() {
        given:
        def isFullyConverted = true
        when:
        String html = emailContentService.getThirdPillarHtml("test_account_1", isFullyConverted, ENGLISH)
        then:
        html.contains('tuleva@tuleva.ee')
        html.contains('test_account_1')
        !html.contains('Open second pillar here:')
    }
}