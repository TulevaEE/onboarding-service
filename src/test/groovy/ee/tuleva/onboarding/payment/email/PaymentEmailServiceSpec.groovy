package ee.tuleva.onboarding.payment.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.email.MandateEmailService
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmail
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailService
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType.REMIND_THIRD_PILLAR_PAYMENT
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment

class PaymentEmailServiceSpec extends Specification {

  EmailService emailService = Mock()
  ScheduledEmailService scheduledEmailService = Mock()
  MandateEmailService mandateEmailService = Mock()

  PaymentEmailService paymentEmailService = new PaymentEmailService(emailService,
      scheduledEmailService,
      mandateEmailService)

  def "send third pillar payment success email"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    def message = new MandrillMessage()
    def mergeVars = [
        "fname": user.firstName,
        "lname": user.lastName,
        "amount": 10.00,
        "currency": EUR,
        "recipient": payment.recipientPersonalCode
    ]
    def tags = ["pillar_3.1", "mandate", "payment"]
    def locale = Locale.ENGLISH
    def mandrillMessageId = "mandrillMessageId123"
    def mandateAttachment = new MessageContent()
    def mandate = new Mandate(mandate: new byte[0])

    scheduledEmailService.cancel(user, REMIND_THIRD_PILLAR_PAYMENT) >> [new ScheduledEmail(
        userId: user.id, mandrillMessageId: mandrillMessageId,
        type: REMIND_THIRD_PILLAR_PAYMENT, mandate: mandate
    )]
    mandateEmailService.getMandateAttachments(user, mandate) >> [mandateAttachment]

    when:
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_success_mandate_en", mergeVars, tags, [mandateAttachment]) >> message
    1 * emailService.send(user, message, "third_pillar_payment_success_mandate_en") >> Optional.of("123")
  }
}
