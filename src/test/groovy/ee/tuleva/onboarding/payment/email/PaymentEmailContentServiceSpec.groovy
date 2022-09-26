package ee.tuleva.onboarding.payment.email

import au.com.origin.snapshots.Expect
import au.com.origin.snapshots.annotations.SnapshotName
import au.com.origin.snapshots.spock.EnableSnapshots
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

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

    when:
    String html = emailContentService.getThirdPillarPaymentSuccessHtml(user, Locale.forLanguageTag(language))

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

    when:
    String html = emailContentService.getThirdPillarSuggestSecondHtml(user, Locale.forLanguageTag(language))

    then:
    expect.scenario(language).toMatchSnapshot(html)

    where:
    language | _
    'en'     | _
    'et'     | _
  }
}
