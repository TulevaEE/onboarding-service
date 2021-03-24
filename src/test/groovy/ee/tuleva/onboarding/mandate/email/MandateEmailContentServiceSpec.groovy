package ee.tuleva.onboarding.mandate.email


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static java.util.Locale.ENGLISH

@SpringBootTest
class MandateEmailContentServiceSpec extends Specification {

    @Autowired
    MandateEmailContentService emailContentService

    def "second pillar message: suggest third pillar when not fully converted to tuleva and not a member (third pillar active)"() {
        given:
        def user = sampleUserNonMember().build()
        def isThirdPillarActive = true
        def isFullyConverted = false
        def pillarSuggestion = new PillarSuggestion(3, isThirdPillarActive, isFullyConverted, user.isMember())

        when:
        String html = emailContentService.getSecondPillarHtml(user, pillarSuggestion, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('Next, set up your third pillar.')
    }

    def "second pillar message: suggest membership when fully converted to tuleva and not a member"() {
        given:
        def user = sampleUserNonMember().build()
        def isThirdPillarActive = true
        def isFullyConverted = true
        def pillarSuggestion = new PillarSuggestion(3, isThirdPillarActive, isFullyConverted, user.isMember())

        when:
        String html = emailContentService.getSecondPillarHtml(user, pillarSuggestion, ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('I would still welcome you to think about becoming a member')
    }

    def "third pillar message: suggest second pillar when not fully converted to tuleva and not a member (second pillar active)"() {
        given:
        def user = sampleUserNonMember().build()
        def isSecondPillarActive = true
        def isFullyConverted = false
        def pillarSuggestion = new PillarSuggestion(2, isSecondPillarActive, isFullyConverted, user.isMember())

        when:
        String html = emailContentService.getThirdPillarHtml(user, pillarSuggestion, "test_account_1", ENGLISH)

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
        def pillarSuggestion = new PillarSuggestion(2, isSecondPillarActive, isFullyConverted, user.isMember())

        when:
        String html = emailContentService.getThirdPillarHtml(user, pillarSuggestion, "test_account_1", ENGLISH)

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
        def pillarSuggestion = new PillarSuggestion(2, isSecondPillarActive, isFullyConverted, user.isMember())

        when:
        String html = emailContentService.getThirdPillarHtml(user, pillarSuggestion, "test_account_1", ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('test_account_1')
    }

    def "third pillar message: only action info when second pillar is not active and is a member"() {
        given:
        def user = sampleUser().build()
        def isSecondPillarActive = false
        def isFullyConverted = false
        def pillarSuggestion = new PillarSuggestion(2, isSecondPillarActive, isFullyConverted, user.isMember())

        when:
        String html = emailContentService.getThirdPillarHtml(user, pillarSuggestion, "test_account_1", ENGLISH)

        then:
        html.contains('You are now saving for your pension alongside me and other Tuleva members.')
        html.contains('test_account_1')
    }

  def "renders 2nd pillar transfer cancellation email"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()
    when:
    String html = emailContentService.getSecondPillarTransferCancellationHtml(user, mandate, ENGLISH)

    then:
    html.contains('You have submitted a cancellation application through Tuleva.')
    html.contains(mandate.getFundTransferExchanges().get(0).getSourceFundIsin())
  }

  def "renders 2nd pillar withdrawal cancellation email"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()
    when:
    String html = emailContentService.getSecondPillarWithdrawalCancellationHtml(user, ENGLISH)

    then:
    html.contains('You have cancelled your 2nd pillar withdrawal application.')
  }
}