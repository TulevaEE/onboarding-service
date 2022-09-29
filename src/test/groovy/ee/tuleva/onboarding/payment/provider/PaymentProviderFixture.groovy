package ee.tuleva.onboarding.payment.provider

class PaymentProviderFixture {
  static PaymentProviderConfiguration aPaymentProviderConfiguration() {
    PaymentProviderBank samplePaymentProviderBankConfiguration = new PaymentProviderBank()
    samplePaymentProviderBankConfiguration.accessKey = "exampleAccessKey"
    samplePaymentProviderBankConfiguration.secretKey = "exampleSecretKeyexampleSecretKeyexampleSecretKey"
    samplePaymentProviderBankConfiguration.bic = "exampleAspsp"
    def configuration = new PaymentProviderConfiguration([(Bank.LHV): samplePaymentProviderBankConfiguration])
    configuration.mapByAccessKey()
    return configuration
  }

  static String aSerializedToken = "eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL3N1Y2Nlc3MiLCJhbW91bnQiOjEwLCJwYXltZW50X2luZm9ybWF0aW9uX3Vuc3RydWN0dXJlZCI6IjMwMTAxMTE5ODI4IiwiY2hlY2tvdXRfZmlyc3RfbmFtZSI6IkpvcmRhbiIsIm1lcmNoYW50X25vdGlmaWNhdGlvbl91cmwiOiJodHRwczovL29uYm9hcmRpbmctc2VydmljZS50dWxldmEuZWUvdjEvcGF5bWVudHMvbm90aWZpY2F0aW9uIiwicHJlc2VsZWN0ZWRfYXNwc3AiOiJleGFtcGxlQXNwc3AiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIxMjM0NDM0MzRcIiwgXCJ1dWlkXCI6IFwiM2FiOTRmMTEtZmI3MS00NDAxLTgwNDMtNWU5MTEyMjcwMzdlXCJ9IiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJwYXltZW50X2luZm9ybWF0aW9uX3N0cnVjdHVyZWQiOiI5OTM0MzI0MzIiLCJjdXJyZW5jeSI6IkVVUiIsImV4cCI6MTYwNjEyNjIwMCwicHJlc2VsZWN0ZWRfbG9jYWxlIjoiZXQiLCJjaGVja291dF9sYXN0X25hbWUiOiJWYWxkbWEifQ.tnHC6GGsg_d2p8lb1UFStCFFa5GajZ3tXW7lavdWpMQ"
  static String anInternalReferenceSerialized = """{"personalCode": "123443434", "uuid": "3ab94f11-fb71-4401-8043-5e911227037e"}"""
  static PaymentReference anInternalReference =
      new PaymentReference("123443434", UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"))
  static String aSerializedCallbackFinalizedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIxMC4wMCIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwibWVyY2hhbnRfcmVmZXJlbmNlIjoie1wicGVyc29uYWxDb2RlXCI6IFwiMTIzNDQzNDM0XCIsIFwidXVpZFwiOiBcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwifSIsInN0YXR1cyI6ImZpbmFsaXplZCIsInBheW1lbnRfbWV0aG9kX25hbWUiOiJTRUIgRWVzdGkiLCJjdXN0b21lcl9pYmFuIjoiRUUwMDAwMTAwMTAxNzI4MTAwMDAiLCJwYXltZW50X3V1aWQiOiI0YWYxZDc5ZS0zMWQ5LTQxZTYtODFiZS0xMjM5YzFjNjQ1NjYiLCJpYXQiOjE2NjQxOTAzMjQsImV4cCI6MTY2NDIwNDcyNH0.4zVwR-cRGT7r3q44uMSt5pvSYhLvWciBZ5qKTIeV1O8"
  static String aSerializedCallbackPendingToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIxMjM0NDM0MzRcIiwgXCJ1dWlkXCI6IFwiM2FiOTRmMTEtZmI3MS00NDAxLTgwNDMtNWU5MTEyMjcwMzdlXCJ9Iiwic3RhdHVzIjoicGVuZGluZyIsInBheW1lbnRfbWV0aG9kX25hbWUiOiJTRUIgRWVzdGkiLCJjdXN0b21lcl9pYmFuIjoiRUUwMDAwMTAwMTAxNzI4MTAwMDAiLCJwYXltZW50X3V1aWQiOiI0YWYxZDc5ZS0zMWQ5LTQxZTYtODFiZS0xMjM5YzFjNjQ1NjYiLCJpYXQiOjE2NjQxOTAzMjQsImV4cCI6MTY2NDIwNDcyNH0.MQCZtdYrU2kNzMu3qAUuD5dHCQP_wB5ZaMjU4djG8RA"
  static String aSerializedCallbackFailedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIxMjM0NDM0MzRcIiwgXCJ1dWlkXCI6IFwiM2FiOTRmMTEtZmI3MS00NDAxLTgwNDMtNWU5MTEyMjcwMzdlXCJ9Iiwic3RhdHVzIjoiYWJhbmRvbmVkIiwicGF5bWVudF9tZXRob2RfbmFtZSI6IlNFQiBFZXN0aSIsImN1c3RvbWVyX2liYW4iOiJFRTAwMDAxMDAxMDE3MjgxMDAwMCIsInBheW1lbnRfdXVpZCI6IjRhZjFkNzllLTMxZDktNDFlNi04MWJlLTEyMzljMWM2NDU2NiIsImlhdCI6MTY2NDE5MDMyNCwiZXhwIjoxNjY0MjA0NzI0fQ.5-KwbLNHJ87uSpPiHJH_4JjfaIGKwyHs2iHp6STpJ9c"

}
