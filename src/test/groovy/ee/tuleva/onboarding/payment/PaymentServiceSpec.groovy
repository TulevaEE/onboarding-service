package ee.tuleva.onboarding.payment

import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment

class PaymentServiceSpec extends Specification {

  PaymentRepository paymentRepository = Mock()
  PaymentService paymentService = new PaymentService(paymentRepository)

  def "can get payments for a user"() {
    given:
    def person = samplePerson()
    def payment = aNewPayment()
    paymentRepository.findAllByUserPersonalCode(person.personalCode) >> [payment]
    when:
    def payments = paymentService.getPayments(person)
    then:
    payments == [payment]
  }
}
