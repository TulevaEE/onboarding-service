package ee.tuleva.onboarding.payment

import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static ee.tuleva.onboarding.payment.PaymentStatus.PENDING

class PaymentServiceSpec extends Specification {

  PaymentRepository paymentRepository = Mock()
  PaymentService paymentService = new PaymentService(paymentRepository)

  def "can get pending payments for a user"() {
    given:
    def person = samplePerson()
    def status = PENDING
    def payment = aNewPayment()
    paymentRepository.findAllByUserPersonalCodeAndStatus(person.personalCode, status) >> [payment]
    when:
    def payments = paymentService.getPayments(person, status)
    then:
    payments == [payment]
  }
}
