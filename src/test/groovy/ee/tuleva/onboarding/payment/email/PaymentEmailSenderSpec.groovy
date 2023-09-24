package ee.tuleva.onboarding.payment.email

import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE
import static java.util.Locale.ENGLISH

class PaymentEmailSenderSpec extends Specification {

  PaymentEmailService paymentEmailService = Mock()

  def paymentEmailSender = new PaymentEmailSender(paymentEmailService)

  def "send emails on payment creation"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    def locale = ENGLISH
    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, payment, locale)

    when:
    paymentEmailSender.sendEmails(paymentCreatedEvent)

    then:
    1 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, locale)
  }

  def "does not send emails on payment creation if member fee payment"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    payment.paymentType = MEMBER_FEE
    def locale = ENGLISH
    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, payment, locale)

    when:
    paymentEmailSender.sendEmails(paymentCreatedEvent)

    then:
    0 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(_, _, _)
  }
}
