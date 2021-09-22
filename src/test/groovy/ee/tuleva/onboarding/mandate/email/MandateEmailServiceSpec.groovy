package ee.tuleva.onboarding.mandate.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static java.time.ZoneOffset.UTC

class MandateEmailServiceSpec extends Specification {

  MandateEmailContentService emailContentService = Mock(MandateEmailContentService)
  EmailService emailService = Mock(EmailService)
  def now = Instant.parse("2021-09-01T10:06:01Z")
  MandateEmailService mandateEmailService = new MandateEmailService(emailService, emailContentService, Clock.fixed(now, UTC))

  def "Send second pillar mandate email"() {
    given:
    def user = sampleUser().build()
    def conversion = notFullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandate()
    def pillarSuggestion = new PillarSuggestion(3, user, contactDetails, conversion)
    def recipients = [new Recipient()]
    def message = new MandrillMessage()
    def subject = "Pensionifondi avaldus"
    def html = "html"
    def tags = ["mandate", "pillar_2", "suggest_3"]

    emailContentService.getSecondPillarHtml(*_) >> html
    emailService.getRecipients(user) >> recipients

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, contactDetails, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(recipients, subject, html, tags, _) >> message
    1 * emailService.send(user, message)
  }

  def "Send third pillar payment details email"() {
    given:
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandate()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    def recipients = [new Recipient()]
    def message = new MandrillMessage()
    def subject = "Sinu 3. samba tähtis info ja avalduse koopia"
    def html = "payment details html"
    def tags = ["mandate"]
    def locale = Locale.ENGLISH

    emailContentService.getThirdPillarPaymentDetailsHtml(user, contactDetails.getPensionAccountNumber(), locale) >> html
    emailService.getRecipients(user) >> recipients

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, contactDetails, locale)

    then:
    1 * emailService.newMandrillMessage(recipients, subject, html, tags, _) >> message
    1 * emailService.send(user, message)
  }

  @Unroll
  def "Send third pillar suggest second pillar email"() {
    given:
    def user = sampleUser().build()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandate()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    def recipients = [new Recipient()]
    def message = new MandrillMessage()
    def subject = "Vaata oma teine sammas üle!"
    def html = "suggest second html"
    def tags = ["suggest_2"]
    def locale = Locale.ENGLISH
    def sendAt = now.plus(3, ChronoUnit.DAYS)

    emailContentService.getThirdPillarSuggestSecondHtml(user, locale) >> html
    emailService.getRecipients(user) >> recipients

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, contactDetails, locale)

    then:
    callCount * emailService.newMandrillMessage(recipients, subject, html, tags, null) >> message
    callCount * emailService.send(user, message, sendAt)

    where:
    conversion          | callCount
    notFullyConverted() | 1
    fullyConverted()    | 0
  }

  def "Sends two third pillar emails"() {
    given:
    def user = sampleUser().build()
    def conversion = notFullyConverted()
    def contactDetails = contactDetailsFixture()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    emailContentService.getThirdPillarPaymentDetailsHtml(*_) >> "html"
    emailService.getRecipients(user) >> [new Recipient()]

    when:
    mandateEmailService.sendMandate(user, sampleMandate(), pillarSuggestion, contactDetails, Locale.ENGLISH)

    then:
    2 * emailService.newMandrillMessage(*_) >> new MandrillMessage()
    2 * emailService.send(*_)
  }

  @Unroll
  def "mandate tagging for 2nd pillar mandates"() {
    given:
    def pillarSuggestion = new PillarSuggestion(3, isThirdPillarActive, isThirdPillarFullyConverted, isMember)

    when:
    def tags = mandateEmailService.getMandateTags(pillarSuggestion)

    then:
    tags == expectedTags

    where:
    isThirdPillarActive | isThirdPillarFullyConverted | isMember || expectedTags
    false               | false                       | false    || ["mandate", "pillar_2", "suggest_3"]
    false               | false                       | true     || ["mandate", "pillar_2", "suggest_3"]
    true                | false                       | false    || ["mandate", "pillar_2", "suggest_3"]
    true                | false                       | true     || ["mandate", "pillar_2", "suggest_3"]
    true                | true                        | false    || ["mandate", "pillar_2", "suggest_member"]
    true                | true                        | true     || ["mandate", "pillar_2"]
  }

  @Unroll
  def "mandate tagging for 3rd pillar mandates"() {
    given:
    def pillarSuggestion = new PillarSuggestion(2, isSecondPillarActive, isSecondPillarFullyConverted, isMember)

    when:
    def tags = mandateEmailService.getMandateTags(pillarSuggestion)

    then:
    tags == expectedTags

    where:
    isSecondPillarActive | isSecondPillarFullyConverted | isMember || expectedTags
    false                | false                        | false    || ["mandate", "pillar_3", "suggest_2"]
    false                | false                        | true     || ["mandate", "pillar_3", "suggest_2"]
    true                 | false                        | false    || ["mandate", "pillar_3", "suggest_2"]
    true                 | false                        | true     || ["mandate", "pillar_3", "suggest_2"]
    true                 | true                         | false    || ["mandate", "pillar_3", "suggest_member"]
    true                 | true                         | true     || ["mandate", "pillar_3"]
  }
}
