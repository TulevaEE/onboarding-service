package ee.tuleva.onboarding.payment

import org.junit.jupiter.api.Test

class PaymentProviderConfigurationSpec {

  @Test
  void returnsAsps() {
    given:
    PaymentProviderConfiguration paymentProviderConfiguration = new PaymentProviderConfiguration()
    when:
    String key = paymentProviderConfiguration.getAccessKey(Bank.LHV)
    then:
    key != null
  }
}
