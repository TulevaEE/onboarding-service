package ee.tuleva.onboarding.payment.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.email.MandateEmailService
import ee.tuleva.onboarding.mandate.email.persistence.Email
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment

class PaymentEmailServiceSpec extends Specification {

  EmailService emailService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  MandateEmailService mandateEmailService = Mock()

  PaymentEmailService paymentEmailService = new PaymentEmailService(emailService,
      emailPersistenceService,
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

    emailPersistenceService.cancel(user, THIRD_PILLAR_PAYMENT_REMINDER_MANDATE) >> [new Email(
        userId: user.id, mandrillMessageId: mandrillMessageId,
        type: THIRD_PILLAR_PAYMENT_REMINDER_MANDATE, mandate: mandate
    )]
    mandateEmailService.getMandateAttachments(user, mandate) >> [mandateAttachment]
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_success_mandate_en", mergeVars, tags, [mandateAttachment]) >> message
    1 * emailService.send(user, message, "third_pillar_payment_success_mandate_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE, mandrillResponse.status)
  }
}
