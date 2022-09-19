package ee.tuleva.onboarding.payment

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification
import static PaymentFixture.aPaymentProviderBankConfiguration
import static PaymentFixture.aSerializedToken
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.payment.PaymentFixture.anInternalReference

class PaymentProviderCallbackServiceSpec extends Specification {
  UserService userService = Mock()
  PaymentProviderCallbackService paymentProviderCallbackService

  Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurations
      = [:]
  void setup() {
    paymentProviderBankConfigurations.put(Bank.LHV.getBeanName(), aPaymentProviderBankConfiguration())
    paymentProviderCallbackService = new PaymentProviderCallbackService(
        paymentProviderBankConfigurations,
        userService,
        new ObjectMapper()
    )
  }

  void processToken() {
    given:
    def token = aSerializedToken
    when:
    1 * userService.findByPersonalCode(anInternalReference.getPersonalCode()) >>
        Optional.of(sampleUser().build())
    paymentProviderCallbackService.processToken(token)
    then:
    true
  }
}
