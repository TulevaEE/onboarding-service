package ee.tuleva.onboarding.notification.email


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

@SpringBootTest
class EmailContentServiceSpec extends Specification {

    @Autowired
    EmailContentService emailContentService

    def "get mandate email html content"() {
        given:
        when:
        String html = emailContentService.getMandateEmailHtml()
        then:
        html.contains('tuleva@tuleva.ee')
    }

    def "get membership email html content"() {
        given:
        def user = sampleUser().build()
        when:
        String html = emailContentService.getMembershipEmailHtml(user)
        then:
        html.contains(user.firstName)
        html.contains(user.lastName)
    }

}
