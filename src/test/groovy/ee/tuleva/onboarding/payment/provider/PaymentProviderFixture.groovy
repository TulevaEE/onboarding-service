package ee.tuleva.onboarding.payment.provider

import ee.tuleva.onboarding.payment.PaymentData

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.TULUNDUSUHISTU


class PaymentProviderFixture {
  static String anAccessKey = "exampleAccessKey"
  static String aSecretKey = "exampleSecretKeyexampleSecretKeyexampleSecretKey"
  static String anAccessKeyTulundusuhistu = "exampleAccessKeyTulundusuhistu"
  static String aSecretKeyTulundusuhistu = "exampleSecretKeyexampleSecretKeyexampleSecretKeyTulundusuhistu"

  static PaymentProviderConfiguration aPaymentProviderConfiguration() {
    PaymentProviderChannel samplePaymentProviderChannelConfiguration = new PaymentProviderChannel()
    samplePaymentProviderChannelConfiguration.accessKey = anAccessKey
    samplePaymentProviderChannelConfiguration.secretKey = aSecretKey
    samplePaymentProviderChannelConfiguration.bic = "exampleAspsp"

    PaymentProviderChannel samplePaymentProviderChannelConfigurationTulundusuhistu = new PaymentProviderChannel()
    samplePaymentProviderChannelConfigurationTulundusuhistu.accessKey = anAccessKeyTulundusuhistu
    samplePaymentProviderChannelConfigurationTulundusuhistu.secretKey = aSecretKeyTulundusuhistu
    samplePaymentProviderChannelConfigurationTulundusuhistu.bic = "exampleAspsp"

    def configuration = new PaymentProviderConfiguration([
        (LHV)           : samplePaymentProviderChannelConfiguration,
        (TULUNDUSUHISTU): samplePaymentProviderChannelConfigurationTulundusuhistu,
    ])
    configuration.mapByAccessKey()
    return configuration
  }

  static String aSerializedPaymentProviderToken =
      "eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL3N1Y2Nlc3MiLCJhbW91bnQiOjEwLjAwLCJwYXltZW50X2luZm9ybWF0aW9uX3Vuc3RydWN0dXJlZCI6IjMwMTAxMTE5ODI4LCBJSzozODgxMjEyMTIxNSwgRUUzNjAwMDAxNzA3IiwiY2hlY2tvdXRfZmlyc3RfbmFtZSI6IkpvcmRhbiIsIm1lcmNoYW50X25vdGlmaWNhdGlvbl91cmwiOiJodHRwczovL29uYm9hcmRpbmctc2VydmljZS50dWxldmEuZWUvdjEvcGF5bWVudHMvbm90aWZpY2F0aW9uIiwicHJlc2VsZWN0ZWRfYXNwc3AiOiJleGFtcGxlQXNwc3AiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwidXVpZFwiOiBcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLCBcInBheW1lbnRUeXBlXCI6IFwiTUVNQkVSX0ZFRVwifSIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwiY3VycmVuY3kiOiJFVVIiLCJleHAiOjE2MDYxMjYyMDAsInByZXNlbGVjdGVkX2xvY2FsZSI6ImV0IiwiY2hlY2tvdXRfbGFzdF9uYW1lIjoiVmFsZG1hIn0.Db8sycKqFeD5u9JW_z39fca-mi2Cqu0zmTDaQMEJ4gc"
  static String aSerializedPaymentProviderTokenForMemberFeePayment =
      "eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL21lbWJlci1zdWNjZXNzIiwiYW1vdW50IjoxMjUsInBheW1lbnRfaW5mb3JtYXRpb25fdW5zdHJ1Y3R1cmVkIjoibWVtYmVyOjM4ODEyMTIxMjE1IiwiY2hlY2tvdXRfZmlyc3RfbmFtZSI6IkpvcmRhbiIsIm1lcmNoYW50X25vdGlmaWNhdGlvbl91cmwiOiJodHRwczovL29uYm9hcmRpbmctc2VydmljZS50dWxldmEuZWUvdjEvcGF5bWVudHMvbm90aWZpY2F0aW9uIiwicHJlc2VsZWN0ZWRfYXNwc3AiOiJleGFtcGxlQXNwc3AiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwidXVpZFwiOiBcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLCBcInBheW1lbnRUeXBlXCI6IFwiTUVNQkVSX0ZFRVwifSIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5VHVsdW5kdXN1aGlzdHUiLCJjdXJyZW5jeSI6IkVVUiIsImV4cCI6MTYwNjEyNjIwMCwicHJlc2VsZWN0ZWRfbG9jYWxlIjoiZXQiLCJjaGVja291dF9sYXN0X25hbWUiOiJWYWxkbWEifQ.8UiRABp9_DnuOsenNKwmi_g_0y4Lezcj-nSY6__9VFk"
  static String anInternalReferenceSerialized = """{"personalCode": "38812121215", "recipientPersonalCode": "38812121215", "uuid": "3ab94f11-fb71-4401-8043-5e911227037e", "paymentType": "MEMBER_FEE"}"""
  static PaymentReference anInternalReference =
      new PaymentReference("38812121215", "38812121215", UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), PaymentData.PaymentType.MEMBER_FEE, Locale.ENGLISH)
  static String aSerializedCallbackFinalizedSinglePaymentToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIxMC4wMCIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwibWVyY2hhbnRfcmVmZXJlbmNlIjoie1wicGVyc29uYWxDb2RlXCI6IFwiMzg4MTIxMjEyMTVcIiwgXCJyZWNpcGllbnRQZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIiwgXCJwYXltZW50VHlwZVwiOiBcIlNJTkdMRVwiLFwibG9jYWxlXCI6XCJlblwifSIsInN0YXR1cyI6ImZpbmFsaXplZCIsInBheW1lbnRfbWV0aG9kX25hbWUiOiJTRUIgRWVzdGkiLCJjdXN0b21lcl9pYmFuIjoiRUUwMDAwMTAwMTAxNzI4MTAwMDAiLCJwYXltZW50X3V1aWQiOiI0YWYxZDc5ZS0zMWQ5LTQxZTYtODFiZS0xMjM5YzFjNjQ1NjYiLCJpYXQiOjE2NjQxOTAzMjQsImV4cCI6MTY2NDIwNDcyNH0.7GhzEIBRrIakCNGkdEtoC75Y-IE86EOqo6fKlU9t7H4"
  static String aSerializedCallbackFinalizedMemberPaymentToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIxMC4wMCIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwibWVyY2hhbnRfcmVmZXJlbmNlIjoie1wicGVyc29uYWxDb2RlXCI6IFwiMzg4MTIxMjEyMTVcIiwgXCJyZWNpcGllbnRQZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIiwgXCJwYXltZW50VHlwZVwiOiBcIk1FTUJFUl9GRUVcIixcImxvY2FsZVwiOlwiZW5cIn0iLCJzdGF0dXMiOiJmaW5hbGl6ZWQiLCJwYXltZW50X21ldGhvZF9uYW1lIjoiU0VCIEVlc3RpIiwiY3VzdG9tZXJfaWJhbiI6IkVFMDAwMDEwMDEwMTcyODEwMDAwIiwicGF5bWVudF91dWlkIjoiNGFmMWQ3OWUtMzFkOS00MWU2LTgxYmUtMTIzOWMxYzY0NTY2IiwiaWF0IjoxNjY0MTkwMzI0LCJleHAiOjE2NjQyMDQ3MjR9.3qudif4nfft0PyEf5W6TbaLXONSB2wDIk-SIDUOCkLY"
  static String aSerializedCallbackPendingToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwidXVpZFwiOiBcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLFwibG9jYWxlXCI6XCJlblwifSIsInN0YXR1cyI6InBlbmRpbmciLCJwYXltZW50X21ldGhvZF9uYW1lIjoiU0VCIEVlc3RpIiwiY3VzdG9tZXJfaWJhbiI6IkVFMDAwMDEwMDEwMTcyODEwMDAwIiwicGF5bWVudF91dWlkIjoiNGFmMWQ3OWUtMzFkOS00MWU2LTgxYmUtMTIzOWMxYzY0NTY2IiwiaWF0IjoxNjY0MTkwMzI0LCJleHAiOjE2NjQyMDQ3MjR9.k1Jfvu2yTHgroE3277Ainn0EpyPqC5JJ0ve4cSAKEY0"
  static String aSerializedCallbackFailedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwidXVpZFwiOiBcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLCBcImxvY2FsZVwiOiBcImVuXCJ9Iiwic3RhdHVzIjoiYWJhbmRvbmVkIiwicGF5bWVudF9tZXRob2RfbmFtZSI6IlNFQiBFZXN0aSIsImN1c3RvbWVyX2liYW4iOiJFRTAwMDAxMDAxMDE3MjgxMDAwMCIsInBheW1lbnRfdXVpZCI6IjRhZjFkNzllLTMxZDktNDFlNi04MWJlLTEyMzljMWM2NDU2NiIsImlhdCI6MTY2NDE5MDMyNCwiZXhwIjoxNjY0MjA0NzI0fQ.LpwRx5Gh4APWFXA0BV4Xk2E-mu8_0rIpu_fP60SBvf8"

}
