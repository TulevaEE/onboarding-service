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

  def "renders third pillar mandate payment details email correctly"() {
    given:
    def user = sampleUser().build()

    when:
    String html = emailContentService.getThirdPillarPaymentDetailsHtml(user, "test_account_1", ENGLISH)

    then:
    html.contains('Welcome Jordan,')
    html.contains('test_account_1')
  }

  def "renders third pillar mandate second pillar suggestion email correctly"() {
    given:
    def user = sampleUser().build()

    when:
    String html = emailContentService.getThirdPillarSuggestSecondHtml(user, ENGLISH)

    then:
    html.contains('Hello, Jordan.')
    html.contains('Should you bring your second pillar to Tuleva?')
  }
}