package ee.tuleva.onboarding.mandate.email

import au.com.origin.snapshots.Expect
import au.com.origin.snapshots.annotations.SnapshotName
import au.com.origin.snapshots.spock.EnableSnapshots
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static java.util.Locale.ENGLISH

@SpringBootTest
@EnableSnapshots
class MandateEmailContentServiceSpec extends Specification {

  @Autowired
  MandateEmailContentService emailContentService

  @SnapshotName("second_pillar_suggest_third")
  def "second pillar message: suggest third pillar when not fully converted to tuleva and not a member (third pillar active)"(Expect expect) {
    given:
    def user = sampleUserNonMember().build()
    def isThirdPillarActive = true
    def isFullyConverted = false
    def pillarSuggestion = new PillarSuggestion(2, isThirdPillarActive, isFullyConverted, user.isMember())

    when:
    String html = emailContentService.getSecondPillarHtml(user, pillarSuggestion, ENGLISH)
    expect.toMatchSnapshot(html)

    then:
    true
  }

  @SnapshotName("second_pillar_suggest_membership")
  def "second pillar message: suggest membership when fully converted to tuleva and not a member"(Expect expect) {
    given:
    def user = sampleUserNonMember().build()
    def isThirdPillarActive = true
    def isFullyConverted = true
    def pillarSuggestion = new PillarSuggestion(2, isThirdPillarActive, isFullyConverted, user.isMember())

    when:
    String html = emailContentService.getSecondPillarHtml(user, pillarSuggestion, ENGLISH)
    expect.toMatchSnapshot(html)

    then:
    true
  }

  @SnapshotName("second_pillar_transfer_cancellation")
  def "renders 2nd pillar transfer cancellation email"(Expect expect) {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()

    when:
    String html = emailContentService.getSecondPillarTransferCancellationHtml(user, mandate, ENGLISH)
    expect.toMatchSnapshot(html)

    then:
    true
  }

  @SnapshotName("second_pillar_withdraw_cancellation")
  def "renders 2nd pillar withdrawal cancellation email"(Expect expect) {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()

    when:
    String html = emailContentService.getSecondPillarWithdrawalCancellationHtml(user, ENGLISH)
    expect.toMatchSnapshot(html)

    then:
    true
  }

  @SnapshotName("third_pillar_payment_details")
  def "renders third pillar mandate payment details email correctly"(Expect expect) {
    given:
    def user = sampleUser().build()

    when:
    String html = emailContentService.getThirdPillarPaymentDetailsHtml(user, "test_account_1", ENGLISH)
    expect.toMatchSnapshot(html)

    then:
    true
  }

  @SnapshotName("third_pillar_suggest_second")
  def "renders third pillar mandate second pillar suggestion email correctly"(Expect expect) {
    given:
    def user = sampleUser().build()

    when:
    String html = emailContentService.getThirdPillarSuggestSecondHtml(user, ENGLISH)
    expect.toMatchSnapshot(html)

    then:
    true
  }
}