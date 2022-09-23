package ee.tuleva.onboarding.payment

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification
import static PaymentFixture.aPaymentProviderBankConfiguration
import static PaymentFixture.aSerializedToken
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static ee.tuleva.onboarding.payment.PaymentFixture.anInternalReference
import static ee.tuleva.onboarding.payment.PaymentFixture.anAmount

class PaymentProviderCallbackServiceSpec extends Specification {
  UserService userService = Mock()
  PaymentProviderCallbackService paymentProviderCallbackService
  PaymentRepository paymentRepository = Mock()

  Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurations
      = [:]
  void setup() {
    paymentProviderBankConfigurations.put(Bank.LHV.getBeanName(), aPaymentProviderBankConfiguration())
    paymentProviderCallbackService = new PaymentProviderCallbackService(
        paymentProviderBankConfigurations,
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
    paymentProviderCallbackService.processToken(token)
    then:
    1 * paymentRepository.save(aNewPayment)
  }
}
