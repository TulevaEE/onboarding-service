package ee.tuleva.onboarding.payment.email

import au.com.origin.snapshots.Expect
import au.com.origin.snapshots.annotations.SnapshotName
import au.com.origin.snapshots.spock.EnableSnapshots
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment

@SpringBootTest
@EnableSnapshots
class PaymentEmailContentServiceSpec extends Specification {

  @Autowired
  PaymentEmailContentService emailContentService

  private Expect expect

  @SnapshotName("third_pillar_payment_success")
  def "#language: renders the third pillar payment success email correctly"() {
    given:
    def user = sampleUser().build()
    def payment = aNewPayment()
    def contactDetails = contactDetailsFixture()
    def locale = Locale.forLanguageTag(language)

    when:
    String html =
        emailContentService.getThirdPillarPaymentSuccessHtml(user, payment, contactDetails, locale)

    then:
    expect.scenario(language).toMatchSnapshot(html)

    where:
    language | _
    'en'     | _
    'et'     | _
  }

  @SnapshotName("third_pillar_suggest_second")
  def "#language: renders the third pillar suggest second email correctly"() {
    given:
    def user = sampleUser().build()
    def locale = Locale.forLanguageTag(language)

    when:
    String html = emailContentService.getThirdPillarSuggestSecondHtml(user, locale)

    then:
    expect.scenario(language).toMatchSnapshot(html)

    where:
    language | _
    'en'     | _
    'et'     | _
  }
}
