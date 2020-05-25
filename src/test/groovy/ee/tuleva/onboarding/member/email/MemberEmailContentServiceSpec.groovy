package ee.tuleva.onboarding.member.email
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

@SpringBootTest
class MemberEmailContentServiceSpec extends Specification {

    @Autowired
    MemberEmailContentService emailContentService

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
