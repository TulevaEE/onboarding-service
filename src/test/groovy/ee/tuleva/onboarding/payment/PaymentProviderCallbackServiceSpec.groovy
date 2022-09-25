package ee.tuleva.onboarding.payment

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static PaymentFixture.aSerializedToken
import static ee.tuleva.onboarding.payment.PaymentFixture.*

class PaymentProviderCallbackServiceSpec extends Specification {
  UserService userService = Mock()
  PaymentProviderCallbackService paymentProviderCallbackService
  PaymentRepository paymentRepository = Mock()

  void setup() {
    paymentProviderCallbackService = new PaymentProviderCallbackService(
        aPaymentProviderConfiguration(),
        userService,
        paymentRepository,
        new ObjectMapper()
    )
  }

  void processToken() {
    given:
    def token = aSerializedToken
    when:
    1 * userService.findByPersonalCode(anInternalReference.getPersonalCode()) >>
        Optional.of(aNewPayment.user)
    1 * paymentRepository.findByInternalReference(anInternalReference.getUuid()) >> Optional.empty()
    paymentProviderCallbackService.processToken(token)
    then:
    1 * paymentRepository.save(aNewPayment)
  }

  def "if payment with a given internal reference exists, then do not create a new one"() {
    given:
    def token = aSerializedToken
    when:
    1 * paymentRepository.findByInternalReference(anInternalReference.getUuid()) >> Optional.of(
        aNewPayment
    )
    paymentProviderCallbackService.processToken(token)
    then:
    0 * paymentRepository.save(_)
  }

}
