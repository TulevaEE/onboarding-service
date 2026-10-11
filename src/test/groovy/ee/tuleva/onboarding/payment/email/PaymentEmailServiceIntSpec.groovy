package ee.tuleva.onboarding.payment.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.mandate.MandateRepository
import ee.tuleva.onboarding.nudge.NudgeDecision
import ee.tuleva.onboarding.notification.email.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.notification.email.EmailType
import ee.tuleva.onboarding.payment.Payment
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_TRANSFER
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment

class PaymentEmailServiceIntSpec extends Specification {

  MandateRepository mandateRepository = Mock()
  EmailService emailService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  PaymentEmailService paymentEmailService =
      new PaymentEmailService(mandateRepository, emailService, emailPersistenceService)

  def "SendThirdPillarPaymentSuccessEmail"() {
    given:
    User user = sampleUser().build()
    Payment payment = aNewSinglePayment()
    def decision = NudgeDecision.of(SECOND_PILLAR_TRANSFER)
    def message = new MandrillMessage()
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }
    emailPersistenceService.cancel(user, EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE) >> []

    when:
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, decision, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_success_mandate_en", _, _, _) >> message
    1 * emailService.send(user, message, "third_pillar_payment_success_mandate_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE, mandrillResponse.status, decision.tag())

    when:
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, decision, Locale.of("et"))

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_success_mandate_et", _, _, _) >> message
    1 * emailService.send(user, message, "third_pillar_payment_success_mandate_et") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE, mandrillResponse.status, decision.tag())
  }
}
