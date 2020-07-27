package ee.tuleva.onboarding.mandate.email

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static java.util.Locale.ENGLISH

@SpringBootTest
class MandateEmailContentServiceSpec extends Specification {

    @Autowired
    MandateEmailContentService emailContentService

    def "second pillar message: suggest membership when third pillar is not active and not a member"() {
        given:
        def user = sampleUserNonMember().build()
        def isThirdPillarActive = false
        def isFullyConverted = false

        when:
        String html = emailContentService.getSecondPillarHtml(user, isFullyConverted, isThirdPillarActive, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('I would still welcome you to think about becoming a member')
    }

    def "second pillar message: suggest third pillar when not fully converted to tuleva and not a member (third pillar active)"() {
        given:
        def user = sampleUserNonMember().build()
        def isThirdPillarActive = true
        def isFullyConverted = false

        when:
        String html = emailContentService.getSecondPillarHtml(user, isFullyConverted, isThirdPillarActive, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('Next, set up your third pillar.')
    }

    def "second pillar message: suggest membership when fully converted to tuleva and not a member"() {
        given:
        def user = sampleUserNonMember().build()
        def isThirdPillarActive = true
        def isFullyConverted = true

        when:
        String html = emailContentService.getSecondPillarHtml(user, isFullyConverted, isThirdPillarActive, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('I would still welcome you to think about becoming a member')
    }

    def "second pillar message: only action info when fully converted to tuleva and is a member"() {
        given:
        def user = sampleUser().build()
        def isThirdPillarActive = true
        def isFullyConverted = true

        when:
        String html = emailContentService.getSecondPillarHtml(user, isFullyConverted, isThirdPillarActive, ENGLISH)

        then:
        html.contains('You have submitted a pension exchange or future contributions fund mandate through Tuleva web application.')
    }

    def "second pillar message: only action info when third pillar is not active and is a member"() {
        given:
        def user = sampleUser().build()
        def isThirdPillarActive = false
        def isFullyConverted = false

        when:
        String html = emailContentService.getSecondPillarHtml(user, isFullyConverted, isThirdPillarActive, ENGLISH)

        then:
        html.contains('You have submitted a pension exchange or future contributions fund mandate through Tuleva web application.')
    }

    def "third pillar message: suggest membership when second pillar is not active and not a member"() {
        given:
        def user = sampleUserNonMember().build()
        def isSecondPillarActive = false
        def isFullyConverted = false

        when:
        String html = emailContentService.getThirdPillarHtml(user, "test_account_1", isFullyConverted, isSecondPillarActive, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('I would still welcome you to think about becoming a member')
        html.contains('test_account_1')
    }

    def "third pillar message: suggest second pillar when not fully converted to tuleva and not a member (second pillar active)"() {
        given:
        def user = sampleUserNonMember().build()
        def isSecondPillarActive = true
        def isFullyConverted = false

        when:
        String html = emailContentService.getThirdPillarHtml(user, "test_account_1", isFullyConverted, isSecondPillarActive, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('Also, make sure your second pillar is in a fund with low fees.')
        html.contains('test_account_1')
    }

    def "third pillar message: suggest membership when fully converted to tuleva and not a member"() {
        given:
        def user = sampleUserNonMember().build()
        def isSecondPillarActive = true
        def isFullyConverted = true

        when:
        String html = emailContentService.getThirdPillarHtml(user, "test_account_1", isFullyConverted, isSecondPillarActive, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('I would still welcome you to think about becoming a member')
        html.contains('test_account_1')
    }

    def "third pillar message: only action info when fully converted to tuleva and is a member"() {
        given:
        def user = sampleUser().build()
        def isSecondPillarActive = true
        def isFullyConverted = true

        when:
        String html = emailContentService.getThirdPillarHtml(user, "test_account_1", isFullyConverted, isSecondPillarActive, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('test_account_1')
    }

    def "third pillar message: only action info when second pillar is not active and is a member"() {
        given:
        def user = sampleUser().build()
        def isSecondPillarActive = false
        def isFullyConverted = false

        when:
        String html = emailContentService.getThirdPillarHtml(user, "test_account_1", isFullyConverted, isSecondPillarActive, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('test_account_1')
    }
}