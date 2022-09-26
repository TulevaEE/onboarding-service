package ee.tuleva.onboarding.payment

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class PaymentFixture {
  static PaymentProviderConfiguration aPaymentProviderConfiguration() {
    PaymentProviderBank samplePaymentProviderBankConfiguration = new PaymentProviderBank()
    samplePaymentProviderBankConfiguration.accessKey = "exampleAccessKey"
    samplePaymentProviderBankConfiguration.secretKey = "exampleSecretKeyexampleSecretKeyexampleSecretKey"
    samplePaymentProviderBankConfiguration.bic = "exampleAspsp"
    def configuration = new PaymentProviderConfiguration([(Bank.LHV): samplePaymentProviderBankConfiguration])
    configuration.mapByBic()
    return configuration
  }

  static String aSerializedToken = "eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL3N1Y2Nlc3MiLCJhbW91bnQiOjEwLCJwYXltZW50X2luZm9ybWF0aW9uX3Vuc3RydWN0dXJlZCI6IjMwMTAxMTE5ODI4IiwiY2hlY2tvdXRfZmlyc3RfbmFtZSI6IkpvcmRhbiIsIm1lcmNoYW50X25vdGlmaWNhdGlvbl91cmwiOiJodHRwczovL29uYm9hcmRpbmctc2VydmljZS50dWxldmEuZWUvdjEvcGF5bWVudHMvbm90aWZpY2F0aW9uIiwicHJlc2VsZWN0ZWRfYXNwc3AiOiJleGFtcGxlQXNwc3AiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIxMjM0NDM0MzRcIiwgXCJ1dWlkXCI6IFwiM2FiOTRmMTEtZmI3MS00NDAxLTgwNDMtNWU5MTEyMjcwMzdlXCJ9IiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJwYXltZW50X2luZm9ybWF0aW9uX3N0cnVjdHVyZWQiOiI5OTM0MzI0MzIiLCJjdXJyZW5jeSI6IkVVUiIsImV4cCI6MTYwNjEyNjIwMCwicHJlc2VsZWN0ZWRfbG9jYWxlIjoiZXQiLCJjaGVja291dF9sYXN0X25hbWUiOiJWYWxkbWEifQ.tnHC6GGsg_d2p8lb1UFStCFFa5GajZ3tXW7lavdWpMQ"
  static anAmount = new BigDecimal(10)
  static String anInternalReferenceSerialized = """{"personalCode": "123443434", "uuid": "3ab94f11-fb71-4401-8043-5e911227037e"}"""
  static PaymentReference anInternalReference =
      new PaymentReference("123443434", UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"))
  static Payment aNewPayment = new Payment(null, sampleUser().build(), anInternalReference.getUuid(), anAmount, PaymentStatus.PENDING, null)

}
