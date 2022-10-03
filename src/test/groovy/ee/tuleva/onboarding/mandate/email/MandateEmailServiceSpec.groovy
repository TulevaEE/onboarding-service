package ee.tuleva.onboarding.mandate.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailService
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType
import ee.tuleva.onboarding.notification.email.EmailService
import org.springframework.context.MessageSource
import org.springframework.context.support.AbstractMessageSource
import spock.lang.Specification

import java.text.MessageFormat
import java.time.Clock
import java.time.Instant

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.HOURS

class MandateEmailServiceSpec extends Specification {

  MandateEmailContentService emailContentService = Mock()
  EmailService emailService = Mock()
  ScheduledEmailService scheduledEmailService = Mock()
  def now = Instant.parse("2021-09-01T10:06:01Z")

  def subject = "subject";
  MessageSource messageSource = new AbstractMessageSource() {
    protected MessageFormat resolveCode(String code, Locale locale) {
      return new MessageFormat(subject)
    }
  }

  MandateEmailService mandateEmailService = new MandateEmailService(
      emailService,
      scheduledEmailService,
      emailContentService,
      Clock.fixed(now, UTC),
      messageSource
  )

  def "Send second pillar mandate email"() {
    given:
    def user = sampleUser().build()
    def conversion = notFullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandate()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    def recipients = [new Recipient()]
    def message = new MandrillMessage()
    def html = "html"
    def tags = ["mandate", "pillar_2", "suggest_3"]

    emailContentService.getSecondPillarHtml(*_) >> html
    emailService.getRecipients(user) >> recipients

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(recipients, subject, html, tags, _) >> message
    1 * emailService.send(user, message)
  }

  def "mandate tagging for 2nd pillar mandates"() {
    given:
    def pillarSuggestion = Mock(PillarSuggestion)
    pillarSuggestion.isSuggestPillar() >> suggestPillar
    pillarSuggestion.isSuggestMembership() >> suggestMembership

    when:
    def tags = mandateEmailService.getSecondPillarMandateTags(pillarSuggestion)

    then:
    tags == expectedTags

    where:
    suggestPillar | suggestMembership || expectedTags
    false         | false             || ["mandate", "pillar_2"]
    false         | true              || ["mandate", "pillar_2", "suggest_member"]
    true          | false             || ["mandate", "pillar_2", "suggest_3"]
    true          | true              || ["mandate", "pillar_2", "suggest_member", "suggest_3"]
  }

  def "schedule third pillar payment reminder email"() {
    given:
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = thirdPillarMandate()
    def pillarSuggestion = new PillarSuggestion(3, user, contactDetails, conversion)
    def recipients = [new Recipient()]
    def message = new MandrillMessage()
    def html = "payment reminder html"
    def tags = ["pillar_3.1", "reminder"]
    def locale = Locale.ENGLISH
    def sendAt = now.plus(1, HOURS)

    emailContentService.getThirdPillarPaymentReminderHtml(user, locale) >> html
    emailService.getRecipients(user) >> recipients

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, locale)

    then:
    1 * emailService.newMandrillMessage(recipients, subject, html, tags, _) >> message
    1 * emailService.send(user, message, sendAt) >> Optional.of("123")
    1 * scheduledEmailService.create(user, "123", ScheduledEmailType.REMIND_THIRD_PILLAR_PAYMENT)
  }

}
