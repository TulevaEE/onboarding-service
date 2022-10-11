package ee.tuleva.onboarding.payment.provider
import ee.tuleva.onboarding.currency.Currency

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

  static String aSerializedPaymentProviderToken = "eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL3N1Y2Nlc3MiLCJhbW91bnQiOjEwLCJwYXltZW50X2luZm9ybWF0aW9uX3Vuc3RydWN0dXJlZCI6IjMwMTAxMTE5ODI4IiwiY2hlY2tvdXRfZmlyc3RfbmFtZSI6IkpvcmRhbiIsIm1lcmNoYW50X25vdGlmaWNhdGlvbl91cmwiOiJodHRwczovL29uYm9hcmRpbmctc2VydmljZS50dWxldmEuZWUvdjEvcGF5bWVudHMvbm90aWZpY2F0aW9uIiwicHJlc2VsZWN0ZWRfYXNwc3AiOiJleGFtcGxlQXNwc3AiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIn0iLCJhY2Nlc3Nfa2V5IjoiZXhhbXBsZUFjY2Vzc0tleSIsInBheW1lbnRfaW5mb3JtYXRpb25fc3RydWN0dXJlZCI6Ijk5MzQzMjQzMiIsImN1cnJlbmN5IjoiRVVSIiwiZXhwIjoxNjA2MTI2MjAwLCJwcmVzZWxlY3RlZF9sb2NhbGUiOiJldCIsImNoZWNrb3V0X2xhc3RfbmFtZSI6IlZhbGRtYSJ9.DvMG4y5T6lxbko8l1W75sgtIeYFyupoV1QEh0OaGRAg"
  static String anInternalReferenceSerialized = """{"personalCode": "38812121215", "uuid": "3ab94f11-fb71-4401-8043-5e911227037e"}"""
  static PaymentReference anInternalReference =
      new PaymentReference("38812121215", UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), Locale.ENGLISH)
  static BigDecimal aPaymentAmount = new BigDecimal("10.00")
  static Currency aPaymentCurrency = Currency.EUR
  static Bank aPaymentBank = Bank.LHV
  static String aSerializedCallbackFinalizedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIxMC4wMCIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwibWVyY2hhbnRfcmVmZXJlbmNlIjoie1wicGVyc29uYWxDb2RlXCI6IFwiMzg4MTIxMjEyMTVcIiwgXCJ1dWlkXCI6IFwiM2FiOTRmMTEtZmI3MS00NDAxLTgwNDMtNWU5MTEyMjcwMzdlXCIsXCJsb2NhbGVcIjpcImVuXCJ9Iiwic3RhdHVzIjoiZmluYWxpemVkIiwicGF5bWVudF9tZXRob2RfbmFtZSI6IlNFQiBFZXN0aSIsImN1c3RvbWVyX2liYW4iOiJFRTAwMDAxMDAxMDE3MjgxMDAwMCIsInBheW1lbnRfdXVpZCI6IjRhZjFkNzllLTMxZDktNDFlNi04MWJlLTEyMzljMWM2NDU2NiIsImlhdCI6MTY2NDE5MDMyNCwiZXhwIjoxNjY0MjA0NzI0fQ.GcEjnfCK8HT4O-l7hJcHz37UNqQS_8cyvk2xSTvdesE"
  static String aSerializedCallbackPendingToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIixcImxvY2FsZVwiOlwiZW5cIn0iLCJzdGF0dXMiOiJwZW5kaW5nIiwicGF5bWVudF9tZXRob2RfbmFtZSI6IlNFQiBFZXN0aSIsImN1c3RvbWVyX2liYW4iOiJFRTAwMDAxMDAxMDE3MjgxMDAwMCIsInBheW1lbnRfdXVpZCI6IjRhZjFkNzllLTMxZDktNDFlNi04MWJlLTEyMzljMWM2NDU2NiIsImlhdCI6MTY2NDE5MDMyNCwiZXhwIjoxNjY0MjA0NzI0fQ.K38bWoGyunyRbIXevyAOngkpbG0JdkgAClaS9NXV2hU"
  static String aSerializedCallbackFailedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIiwgXCJsb2NhbGVcIjogXCJlblwifSIsInN0YXR1cyI6ImFiYW5kb25lZCIsInBheW1lbnRfbWV0aG9kX25hbWUiOiJTRUIgRWVzdGkiLCJjdXN0b21lcl9pYmFuIjoiRUUwMDAwMTAwMTAxNzI4MTAwMDAiLCJwYXltZW50X3V1aWQiOiI0YWYxZDc5ZS0zMWQ5LTQxZTYtODFiZS0xMjM5YzFjNjQ1NjYiLCJpYXQiOjE2NjQxOTAzMjQsImV4cCI6MTY2NDIwNDcyNH0.rYeOU0aafiL73knH2C0SDLqE9-W3rH_S0g7a6vbjokY"

}
