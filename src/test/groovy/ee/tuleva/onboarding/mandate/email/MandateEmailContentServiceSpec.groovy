package ee.tuleva.onboarding.mandate.email

import au.com.origin.snapshots.Expect
import au.com.origin.snapshots.annotations.SnapshotName
import au.com.origin.snapshots.spock.EnableSnapshots
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

@SpringBootTest
@EnableSnapshots
class MandateEmailContentServiceSpec extends Specification {

  @Autowired
  MandateEmailContentService emailContentService

  private Expect expect

  @SnapshotName("second_pillar_suggest_third")
  def "#language: second pillar message: suggest third pillar when not fully converted to tuleva and not a member (third pillar active)"() {
    given:
    def user = sampleUserNonMember().build()
    def pillarSuggestion = Mock(PillarSuggestion)
    Instant mandateDate = Instant.parse("2021-11-30T10:00:00Z")
    pillarSuggestion.isSuggestPillar() >> true
    pillarSuggestion.isSuggestMembership() >> false

    when:
    String html = emailContentService.getSecondPillarHtml(
        user, mandateDate, pillarSuggestion, Locale.forLanguageTag(language))

    then:
    expect.scenario(language).toMatchSnapshot(html)

    where:
    language | _
    'en'     | _
    'et'     | _
  }

  @SnapshotName("second_pillar_suggest_membership")
  def "#language: second pillar message: suggest membership when fully converted to tuleva and not a member"() {
    given:
    def user = sampleUserNonMember().build()
    def pillarSuggestion = Mock(PillarSuggestion)
    Instant mandateDate = Instant.parse("2021-11-30T10:00:00Z")
    pillarSuggestion.isSuggestPillar() >> false
    pillarSuggestion.isSuggestMembership() >> true

    when:
    String html = emailContentService.getSecondPillarHtml(user, mandateDate, pillarSuggestion, Locale.forLanguageTag(language))

    then:
    expect.scenario(language).toMatchSnapshot(html)

    where:
    language | _
    'en'     | _
    'et'     | _
  }

  @SnapshotName("second_pillar_transfer_cancellation")
  def "#language: renders 2nd pillar transfer cancellation email"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()

    when:
    String html = emailContentService.getSecondPillarTransferCancellationHtml(user, mandate, Locale.forLanguageTag(language))

    then:
    expect.scenario(language).toMatchSnapshot(html)

    where:
    language | _
    'en'     | _
    'et'     | _
  }

  @SnapshotName("second_pillar_withdraw_cancellation")
  def "#language: renders 2nd pillar withdrawal cancellation email"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()

    when:
    String html = emailContentService.getSecondPillarWithdrawalCancellationHtml(user, Locale.forLanguageTag(language))

    then:
    expect.scenario(language).toMatchSnapshot(html)

    where:
    language | _
    'en'     | _
    'et'     | _
  }

  @SnapshotName("third_pillar_payment_reminder")
  def "#language: renders third pillar mandate payment reminder email correctly"() {
    given:
    def user = sampleUser().build()

    when:
    String html = emailContentService.getThirdPillarPaymentReminderHtml(user, Locale.forLanguageTag(language))

    then:
    expect.scenario(language).toMatchSnapshot(html)

    where:
    language | _
    'en'     | _
    'et'     | _
  }
}
