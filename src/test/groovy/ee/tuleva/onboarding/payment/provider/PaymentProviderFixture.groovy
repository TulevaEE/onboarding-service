package ee.tuleva.onboarding.payment.provider


import static ee.tuleva.onboarding.payment.PaymentData.Bank

class PaymentProviderFixture {
  static String anAccessKey = "exampleAccessKey"
  static String aSecretKey = "exampleSecretKeyexampleSecretKeyexampleSecretKey"

  static PaymentProviderConfiguration aPaymentProviderConfiguration() {
    PaymentProviderBank samplePaymentProviderBankConfiguration = new PaymentProviderBank()
    samplePaymentProviderBankConfiguration.accessKey = anAccessKey
    samplePaymentProviderBankConfiguration.secretKey = aSecretKey
    samplePaymentProviderBankConfiguration.bic = "exampleAspsp"
    def configuration = new PaymentProviderConfiguration([(Bank.LHV): samplePaymentProviderBankConfiguration])
    configuration.mapByAccessKey()
    return configuration
  }

  static String aSerializedPaymentProviderToken =
      "eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL3N1Y2Nlc3MiLCJhbW91bnQiOjEwLjAwLCJwYXltZW50X2luZm9ybWF0aW9uX3Vuc3RydWN0dXJlZCI6IjMwMTAxMTE5ODI4LCBJSzozODgxMjEyMTIxNSwgRUUzNjAwMDAxNzA3IiwiY2hlY2tvdXRfZmlyc3RfbmFtZSI6IkpvcmRhbiIsIm1lcmNoYW50X25vdGlmaWNhdGlvbl91cmwiOiJodHRwczovL29uYm9hcmRpbmctc2VydmljZS50dWxldmEuZWUvdjEvcGF5bWVudHMvbm90aWZpY2F0aW9uIiwicHJlc2VsZWN0ZWRfYXNwc3AiOiJleGFtcGxlQXNwc3AiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIHV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIn0iLCJhY2Nlc3Nfa2V5IjoiZXhhbXBsZUFjY2Vzc0tleSIsImN1cnJlbmN5IjoiRVVSIiwiZXhwIjoxNjA2MTI2MjAwLCJwcmVzZWxlY3RlZF9sb2NhbGUiOiJldCIsImNoZWNrb3V0X2xhc3RfbmFtZSI6IlZhbGRtYSJ9.nCExWw2XMYkWIpuoFtgoPaO-X38WeBrFtdqMJF1qr4M"
  static String anInternalReferenceSerialized = """{"personalCode": "38812121215", "recipientPersonalCode": "38812121215", uuid": "3ab94f11-fb71-4401-8043-5e911227037e"}"""
  static PaymentReference anInternalReference =
      new PaymentReference("38812121215", "38812121215", UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), Locale.ENGLISH)
  static String aSerializedCallbackFinalizedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIxMC4wMCIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwibWVyY2hhbnRfcmVmZXJlbmNlIjoie1wicGVyc29uYWxDb2RlXCI6IFwiMzg4MTIxMjEyMTVcIiwgXCJyZWNpcGllbnRQZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIixcImxvY2FsZVwiOlwiZW5cIn0iLCJzdGF0dXMiOiJmaW5hbGl6ZWQiLCJwYXltZW50X21ldGhvZF9uYW1lIjoiU0VCIEVlc3RpIiwiY3VzdG9tZXJfaWJhbiI6IkVFMDAwMDEwMDEwMTcyODEwMDAwIiwicGF5bWVudF91dWlkIjoiNGFmMWQ3OWUtMzFkOS00MWU2LTgxYmUtMTIzOWMxYzY0NTY2IiwiaWF0IjoxNjY0MTkwMzI0LCJleHAiOjE2NjQyMDQ3MjR9.quaEJJWq0mcVtJ7wrX6mwlv4UYWtzZG6riOVI0UYX7Y"
  static String aSerializedCallbackPendingToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwidXVpZFwiOiBcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLFwibG9jYWxlXCI6XCJlblwifSIsInN0YXR1cyI6InBlbmRpbmciLCJwYXltZW50X21ldGhvZF9uYW1lIjoiU0VCIEVlc3RpIiwiY3VzdG9tZXJfaWJhbiI6IkVFMDAwMDEwMDEwMTcyODEwMDAwIiwicGF5bWVudF91dWlkIjoiNGFmMWQ3OWUtMzFkOS00MWU2LTgxYmUtMTIzOWMxYzY0NTY2IiwiaWF0IjoxNjY0MTkwMzI0LCJleHAiOjE2NjQyMDQ3MjR9.k1Jfvu2yTHgroE3277Ainn0EpyPqC5JJ0ve4cSAKEY0"
  static String aSerializedCallbackFailedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwidXVpZFwiOiBcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLCBcImxvY2FsZVwiOiBcImVuXCJ9Iiwic3RhdHVzIjoiYWJhbmRvbmVkIiwicGF5bWVudF9tZXRob2RfbmFtZSI6IlNFQiBFZXN0aSIsImN1c3RvbWVyX2liYW4iOiJFRTAwMDAxMDAxMDE3MjgxMDAwMCIsInBheW1lbnRfdXVpZCI6IjRhZjFkNzllLTMxZDktNDFlNi04MWJlLTEyMzljMWM2NDU2NiIsImlhdCI6MTY2NDE5MDMyNCwiZXhwIjoxNjY0MjA0NzI0fQ.LpwRx5Gh4APWFXA0BV4Xk2E-mu8_0rIpu_fP60SBvf8"

}
