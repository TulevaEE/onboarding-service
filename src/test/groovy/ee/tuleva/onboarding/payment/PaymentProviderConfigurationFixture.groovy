package ee.tuleva.onboarding.payment

class PaymentProviderConfigurationFixture {
  static PaymentProviderBankConfiguration aPaymentProviderBankConfiguration() {
    PaymentProviderBankConfiguration samplePaymentProviderBankConfiguration = new PaymentProviderBankConfiguration()
    samplePaymentProviderBankConfiguration.accessKey = "exampleAccessKey"
    samplePaymentProviderBankConfiguration.secretKey = "exampleSecretKeyexampleSecretKeyexampleSecretKey"
    samplePaymentProviderBankConfiguration.bic = "exampleAspsp"
    return samplePaymentProviderBankConfiguration
  }
}
