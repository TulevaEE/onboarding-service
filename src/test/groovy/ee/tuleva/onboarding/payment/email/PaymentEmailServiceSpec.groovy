package ee.tuleva.onboarding.payment.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.email.MandateEmailService
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmail
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailService
import ee.tuleva.onboarding.notification.email.EmailService
import org.springframework.context.MessageSource
import org.springframework.context.support.AbstractMessageSource
import spock.lang.Specification

import java.text.MessageFormat
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType.REMIND_THIRD_PILLAR_PAYMENT
import static ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType.SUGGEST_SECOND_PILLAR
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static java.time.ZoneOffset.UTC

class PaymentEmailServiceSpec extends Specification {

  PaymentEmailContentService emailContentService = Mock()
  EmailService emailService = Mock()
  ScheduledEmailService scheduledEmailService = Mock()
  MandateEmailService mandateEmailService = Mock()
  def now = Instant.parse("2021-09-01T10:06:01Z")

  def subject = "subject";
  MessageSource messageSource = new AbstractMessageSource() {
    protected MessageFormat resolveCode(String code, Locale locale) {
      return new MessageFormat(subject)
    }
  }

  PaymentEmailService paymentEmailService = new PaymentEmailService(emailService,
      scheduledEmailService,
      emailContentService,
      mandateEmailService,
      Clock.fixed(now, UTC),
      messageSource)

  def "send third pillar payment success email"() {
    given:
    def user = sampleUser().build()
    def payment = aNewPayment()
    def contactDetails = contactDetailsFixture()
    def recipients = [new MandrillMessage.Recipient()]
    def message = new MandrillMessage()
    def html = "payment success html"
    def tags = ["pillar_3.1", "mandate", "payment"]
    def locale = Locale.ENGLISH
    def mandrillMessageId = "mandrillMessageId123"
    def mandateAttachment = new MandrillMessage.MessageContent()
    def mandate = new Mandate(mandate: new byte[0])

    emailContentService.getThirdPillarPaymentSuccessHtml(user, payment, contactDetails, locale) >> html
    emailService.getRecipients(user) >> recipients
    scheduledEmailService.cancel(user, REMIND_THIRD_PILLAR_PAYMENT) >> [new ScheduledEmail(
        userId: user.id, mandrillMessageId: mandrillMessageId,
        type: REMIND_THIRD_PILLAR_PAYMENT, mandate: mandate
    )]
    mandateEmailService.getMandateAttachments(user, mandate) >> [mandateAttachment]

    when:
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, contactDetails, locale)

    then:
    1 * emailService.newMandrillMessage(recipients, subject, html, tags, [mandateAttachment]) >> message
    1 * emailService.send(user, message) >> Optional.of("123")
  }

  def "schedule third pillar suggest second pillar email"() {
    given:
    def user = sampleUser().build()
    def recipients = [new MandrillMessage.Recipient()]
    def message = new MandrillMessage()
    def html = "suggest second html"
    def tags = ["pillar_3.1", "suggest_2"]
    def locale = Locale.ENGLISH
    def sendAt = now.plus(3, ChronoUnit.DAYS)

    emailContentService.getThirdPillarSuggestSecondHtml(user, locale) >> html
    emailService.getRecipients(user) >> recipients

    when:
    paymentEmailService.scheduleThirdPillarSuggestSecondEmail(user, locale)

    then:
    1 * emailService.newMandrillMessage(recipients, subject, html, tags, null) >> message
    1 * emailService.send(user, message, sendAt) >> Optional.of("123")
    1 * scheduledEmailService.create(user, "123", SUGGEST_SECOND_PILLAR)
  }
}
