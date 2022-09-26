package ee.tuleva.onboarding.payment.email

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
import java.time.temporal.ChronoUnit

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static java.time.ZoneOffset.UTC

class PaymentEmailServiceSpec extends Specification {

  PaymentEmailContentService emailContentService = Mock()
  EmailService emailService = Mock()
  ScheduledEmailService scheduledEmailService = Mock()
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
      Clock.fixed(now, UTC),
      messageSource)

  def "send third pillar payment success email"() {
    given:
    def user = sampleUser().build()
    def recipients = [new MandrillMessage.Recipient()]
    def message = new MandrillMessage()
    def html = "payment success html"
    def tags = ["pillar_3.1", "mandate", "payment"]
    def locale = Locale.ENGLISH

    emailContentService.getThirdPillarPaymentSuccessHtml(user, locale) >> html
    emailService.getRecipients(user) >> recipients

    when:
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, locale)

    then:
    1 * emailService.newMandrillMessage(recipients, subject, html, tags, []) >> message
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
    1 * scheduledEmailService.create(user, "123", ScheduledEmailType.SUGGEST_SECOND_PILLAR)
  }
}
