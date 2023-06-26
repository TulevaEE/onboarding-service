package ee.tuleva.onboarding.payment.email


import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static java.util.Locale.ENGLISH

class PaymentEmailSenderSpec extends Specification {

  PaymentEmailService paymentEmailService = Mock()

  def paymentEmailSender = new PaymentEmailSender(paymentEmailService)

  def "send emails on payment creation"() {
    given:
    def user = sampleUser().build()
    def payment = aNewPayment()
    def locale = ENGLISH
    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, aNewPayment(), locale)

    when:
    paymentEmailSender.sendEmails(paymentCreatedEvent)

    then:
    1 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, locale)
  }
}
