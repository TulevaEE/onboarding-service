package ee.tuleva.onboarding.mandate.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.notification.email.EmailService
import org.springframework.context.MessageSource
import org.springframework.context.support.AbstractMessageSource
import spock.lang.Specification

import java.text.MessageFormat
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate
import static java.time.ZoneOffset.UTC

class MandateEmailServiceSpec extends Specification {

  MandateEmailContentService emailContentService = Mock(MandateEmailContentService)
  EmailService emailService = Mock(EmailService)
  def now = Instant.parse("2021-09-01T10:06:01Z")

  def subject = "subject";
  MessageSource messageSource = new AbstractMessageSource() {

    protected MessageFormat resolveCode(String code, Locale locale) {
      return new MessageFormat(subject);
    }
  };

  MandateEmailService mandateEmailService = new MandateEmailService(
      emailService,
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
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, contactDetails, Locale.ENGLISH)

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

  def "Send third pillar payment details email"() {
    given:
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = thirdPillarMandate()
    def pillarSuggestion = new PillarSuggestion(3, user, contactDetails, conversion)
    def recipients = [new Recipient()]
    def message = new MandrillMessage()
    def html = "payment details html"
    def tags = ["pillar_3.1", "mandate"]
    def locale = Locale.ENGLISH

    emailContentService
        .getThirdPillarPaymentDetailsHtml(user, contactDetails.getPensionAccountNumber(), locale)
        >> html
    emailService.getRecipients(user) >> recipients

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, contactDetails, locale)

    then:
    1 * emailService.newMandrillMessage(recipients, subject, html, tags, _) >> message
    1 * emailService.send(user, message)
  }

  def "Send third pillar suggest second pillar email"() {
    given:
    def user = sampleUser().build()
    def contactDetails = contactDetailsFixture()
    contactDetails.setSecondPillarActive(pillarActive)
    def mandate = thirdPillarMandate()
    def pillarSuggestion = new PillarSuggestion(3, user, contactDetails, notFullyConverted())
    def recipients = [new Recipient()]
    def message = new MandrillMessage()
    def html = "suggest second html"
    def tags = ["pillar_3.1", "suggest_2"]
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
    pillarActive | callCount
    false        | 1
    true         | 0
  }

  def "Sends two third pillar emails"() {
    given:
    def user = sampleUser().build()
    def conversion = notFullyConverted()
    def contactDetails = contactDetailsFixture()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    emailContentService.getThirdPillarPaymentDetailsHtml(*_) >> "html"
    emailService.getRecipients(user) >> [new Recipient()]
    emailService.newMandrillMessage(*_) >> new MandrillMessage()

    when:
    mandateEmailService
        .sendMandate(user, thirdPillarMandate(), pillarSuggestion, contactDetails, Locale.ENGLISH)

    then:
    2 * emailService.send(*_)
  }
}
