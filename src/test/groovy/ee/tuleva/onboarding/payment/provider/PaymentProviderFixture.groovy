package ee.tuleva.onboarding.payment.provider

import ee.tuleva.onboarding.payment.PaymentData
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentChannel
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentChannelConfiguration

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.TULUNDUSUHISTU


class PaymentProviderFixture {
  static String anAccessKey = "exampleAccessKey"
  static String aSecretKey = "exampleSecretKeyexampleSecretKeyexampleSecretKey"
  static String anAccessKeyTulundusuhistu = "exampleAccessKeyTulundusuhistu"
  static String aSecretKeyTulundusuhistu = "exampleSecretKeyexampleSecretKeyexampleSecretKeyTulundusuhistu"

  static MontonioPaymentChannelConfiguration aPaymentProviderConfiguration() {
    MontonioPaymentChannel samplePaymentProviderChannelConfiguration = new MontonioPaymentChannel()
    samplePaymentProviderChannelConfiguration.accessKey = anAccessKey
    samplePaymentProviderChannelConfiguration.secretKey = aSecretKey
    samplePaymentProviderChannelConfiguration.bic = "exampleAspsp"

    MontonioPaymentChannel samplePaymentProviderChannelConfigurationTulundusuhistu = new MontonioPaymentChannel()
    samplePaymentProviderChannelConfigurationTulundusuhistu.accessKey = anAccessKeyTulundusuhistu
    samplePaymentProviderChannelConfigurationTulundusuhistu.secretKey = aSecretKeyTulundusuhistu
    samplePaymentProviderChannelConfigurationTulundusuhistu.bic = "exampleAspsp"

    def configuration = new MontonioPaymentChannelConfiguration([
        (LHV)           : samplePaymentProviderChannelConfiguration,
        (TULUNDUSUHISTU): samplePaymentProviderChannelConfigurationTulundusuhistu,
    ])
    configuration.mapByAccessKey()
    return configuration
  }

  // Use jwt.io to edit payload, sign with exampleSecret from mocked PAYMENT_SECRET_LHV
  static String aSerializedOrderCreationToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhY2Nlc3NLZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwibWVyY2hhbnRSZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjpcIjM4ODEyMTIxMjE1XCIsXCJyZWNpcGllbnRQZXJzb25hbENvZGVcIjpcIjM4ODEyMTIxMjE1XCIsXCJ1dWlkXCI6XCJmY2M1YmE2NS1lYWQ0LTRiYTItYTM4Mi1iOGI4MGQ1NzYzMjBcIixcInBheW1lbnRUeXBlXCI6XCJTSU5HTEVcIixcImxvY2FsZVwiOlwiZW5fVVNcIn0iLCJyZXR1cm5VcmwiOiJodHRwOi8vbG9jYWxob3N0OjkwMDAvdjEvcGF5bWVudHMvc3VjY2VzcyIsIm5vdGlmaWNhdGlvblVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMC92MS9wYXltZW50cy9ub3RpZmljYXRpb24iLCJncmFuZFRvdGFsIjoxMC4wMCwiY3VycmVuY3kiOiJFVVIiLCJleHAiOjE3MTk5MDk2MDUsInBheW1lbnQiOnsibWV0aG9kIjoicGF5bWVudEluaXRpYXRpb24iLCJhbW91bnQiOjEwLjAwLCJjdXJyZW5jeSI6IkVVUiIsIm1ldGhvZE9wdGlvbnMiOnsicHJlZmVycmVkQ291bnRyeSI6IkVFIiwicHJlZmVycmVkUHJvdmlkZXIiOiJMSFZCRUUyMiIsInByZWZlcnJlZExvY2FsZSI6ImVuIiwicGF5bWVudERlc2NyaXB0aW9uIjoiMzAxMDExMTk4MjgsIElLOjM4ODEyMTIxMjE1LCBFRTM2MDAwMDE3MDcifX0sImxvY2FsZSI6ImVuIn0.gKb0ApFip1ikbq4_wxvcRE7FBQF7S3v7IxdLjiY2dF8"

  static String aSerializedPaymentProviderToken =
      "eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL3N1Y2Nlc3MiLCJhbW91bnQiOjEwLjAwLCJwcmVzZWxlY3RlZF9hc3BzcCI6ImV4YW1wbGVBc3BzcCIsIm1lcmNoYW50X3JlZmVyZW5jZSI6IntcInBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwicmVjaXBpZW50UGVyc29uYWxDb2RlXCI6IFwiMzg4MTIxMjEyMTVcIiwgXCJ1dWlkXCI6IFwiM2FiOTRmMTEtZmI3MS00NDAxLTgwNDMtNWU5MTEyMjcwMzdlXCIsIFwicGF5bWVudFR5cGVcIjogXCJNRU1CRVJfRkVFXCJ9IiwicGF5bWVudF9pbmZvcm1hdGlvbl91bnN0cnVjdHVyZWQiOiIzMDEwMTExOTgyOCwgSUs6Mzg4MTIxMjEyMTUsIEVFMzYwMDAwMTcwNyIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwiY2hlY2tvdXRfZmlyc3RfbmFtZSI6IkpvcmRhbiIsImN1cnJlbmN5IjoiRVVSIiwibWVyY2hhbnRfbm90aWZpY2F0aW9uX3VybCI6Imh0dHBzOi8vb25ib2FyZGluZy1zZXJ2aWNlLnR1bGV2YS5lZS92MS9wYXltZW50cy9ub3RpZmljYXRpb24iLCJleHAiOjE2MDYxMjYyMDAsInByZXNlbGVjdGVkX2xvY2FsZSI6ImV0IiwiY2hlY2tvdXRfbGFzdF9uYW1lIjoiVmFsZG1hIn0.jthny16B9-O1VVSmcSLkXCLTUfJngvQj3MbgjSK89c8"
  static String aSerializedPaymentProviderTokenForMemberFeePayment =
      "eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL21lbWJlci1zdWNjZXNzIiwiYW1vdW50IjoxMjUsInByZXNlbGVjdGVkX2FzcHNwIjoiZXhhbXBsZUFzcHNwIiwibWVyY2hhbnRfcmVmZXJlbmNlIjoie1wicGVyc29uYWxDb2RlXCI6IFwiMzg4MTIxMjEyMTVcIiwgXCJyZWNpcGllbnRQZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIiwgXCJwYXltZW50VHlwZVwiOiBcIk1FTUJFUl9GRUVcIn0iLCJwYXltZW50X2luZm9ybWF0aW9uX3Vuc3RydWN0dXJlZCI6Im1lbWJlcjozODgxMjEyMTIxNSIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5VHVsdW5kdXN1aGlzdHUiLCJjaGVja291dF9maXJzdF9uYW1lIjoiSm9yZGFuIiwiY3VycmVuY3kiOiJFVVIiLCJtZXJjaGFudF9ub3RpZmljYXRpb25fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL25vdGlmaWNhdGlvbiIsImV4cCI6MTYwNjEyNjIwMCwicHJlc2VsZWN0ZWRfbG9jYWxlIjoiZXQiLCJjaGVja291dF9sYXN0X25hbWUiOiJWYWxkbWEifQ.51_as3GMFCdA9wm7BpZXexxCVZGNYZi5eFPMW7nycv0"
  static String anInternalReferenceSerialized = """{"personalCode": "38812121215", "recipientPersonalCode": "38812121215", "uuid": "3ab94f11-fb71-4401-8043-5e911227037e", "paymentType": "MEMBER_FEE"}"""
  static PaymentReference anInternalReference =
      new PaymentReference("38812121215", "38812121215", UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), PaymentData.PaymentType.MEMBER_FEE, Locale.ENGLISH)
  static String aSerializedCallbackFinalizedMemberPaymentToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIxMC4wMCIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwibWVyY2hhbnRfcmVmZXJlbmNlIjoie1wicGVyc29uYWxDb2RlXCI6IFwiMzg4MTIxMjEyMTVcIiwgXCJyZWNpcGllbnRQZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIiwgXCJwYXltZW50VHlwZVwiOiBcIk1FTUJFUl9GRUVcIixcImxvY2FsZVwiOlwiZW5cIn0iLCJzdGF0dXMiOiJmaW5hbGl6ZWQiLCJwYXltZW50X21ldGhvZF9uYW1lIjoiU0VCIEVlc3RpIiwiY3VzdG9tZXJfaWJhbiI6IkVFMDAwMDEwMDEwMTcyODEwMDAwIiwicGF5bWVudF91dWlkIjoiNGFmMWQ3OWUtMzFkOS00MWU2LTgxYmUtMTIzOWMxYzY0NTY2IiwiaWF0IjoxNjY0MTkwMzI0LCJleHAiOjE2NjQyMDQ3MjR9.3qudif4nfft0PyEf5W6TbaLXONSB2wDIk-SIDUOCkLY"
  static String aSerializedCallbackPendingToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwidXVpZFwiOiBcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLFwibG9jYWxlXCI6XCJlblwifSIsInN0YXR1cyI6InBlbmRpbmciLCJwYXltZW50X21ldGhvZF9uYW1lIjoiU0VCIEVlc3RpIiwiY3VzdG9tZXJfaWJhbiI6IkVFMDAwMDEwMDEwMTcyODEwMDAwIiwicGF5bWVudF91dWlkIjoiNGFmMWQ3OWUtMzFkOS00MWU2LTgxYmUtMTIzOWMxYzY0NTY2IiwiaWF0IjoxNjY0MTkwMzI0LCJleHAiOjE2NjQyMDQ3MjR9.k1Jfvu2yTHgroE3277Ainn0EpyPqC5JJ0ve4cSAKEY0"
  static String aSerializedCallbackFailedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbW91bnQiOiIyLjAwIiwiYWNjZXNzX2tleSI6ImV4YW1wbGVBY2Nlc3NLZXkiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwidXVpZFwiOiBcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLCBcImxvY2FsZVwiOiBcImVuXCJ9Iiwic3RhdHVzIjoiYWJhbmRvbmVkIiwicGF5bWVudF9tZXRob2RfbmFtZSI6IlNFQiBFZXN0aSIsImN1c3RvbWVyX2liYW4iOiJFRTAwMDAxMDAxMDE3MjgxMDAwMCIsInBheW1lbnRfdXVpZCI6IjRhZjFkNzllLTMxZDktNDFlNi04MWJlLTEyMzljMWM2NDU2NiIsImlhdCI6MTY2NDE5MDMyNCwiZXhwIjoxNjY0MjA0NzI0fQ.LpwRx5Gh4APWFXA0BV4Xk2E-mu8_0rIpu_fP60SBvf8"

  static String aSerializedCallbackFinalizedSinglePaymentTokenV2Api = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1dWlkIjoiM2FiOTRmMTEtZmI3MS00NDAxLTgwNDMtNWU5MTEyMjcwMzdlIiwiYWNjZXNzS2V5IjoiZXhhbXBsZUFjY2Vzc0tleSIsImdyYW5kVG90YWwiOjEwLCJtZXJjaGFudFJlZmVyZW5jZSI6IntcInBlcnNvbmFsQ29kZVwiOlwiMzg4MTIxMjEyMTVcIixcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOlwiMzg4MTIxMjEyMTVcIixcInV1aWRcIjpcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLFwicGF5bWVudFR5cGVcIjpcIlNJTkdMRVwiLFwibG9jYWxlXCI6XCJldFwifSIsIm1lcmNoYW50UmVmZXJlbmNlRGlzcGxheSI6IntcInBlcnNvbmFsQ29kZVwiOlwiMzg4MTIxMjEyMTVcIixcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOlwiMzg4MTIxMjEyMTVcIixcInV1aWRcIjpcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLFwicGF5bWVudFR5cGVcIjpcIlNJTkdMRVwiLFwibG9jYWxlXCI6XCJldFwifSIsInBheW1lbnRTdGF0dXMiOiJQQUlEIiwicGF5bWVudE1ldGhvZCI6InBheW1lbnRJbml0aWF0aW9uIiwicGF5bWVudFByb3ZpZGVyTmFtZSI6IkxIViBFc3RvbmlhIiwic2VuZGVySWJhbiI6bnVsbCwic2VuZGVyTmFtZSI6bnVsbCwiY3VycmVuY3kiOiJFVVIiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjpcIjM4ODEyMTIxMjE1XCIsXCJyZWNpcGllbnRQZXJzb25hbENvZGVcIjpcIjM4ODEyMTIxMjE1XCIsXCJ1dWlkXCI6XCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIixcInBheW1lbnRUeXBlXCI6XCJTSU5HTEVcIixcImxvY2FsZVwiOlwiZXRcIn0iLCJtZXJjaGFudF9yZWZlcmVuY2VfZGlzcGxheSI6IntcInBlcnNvbmFsQ29kZVwiOlwiMzg4MTIxMjEyMTVcIixcInJlY2lwaWVudFBlcnNvbmFsQ29kZVwiOlwiMzg4MTIxMjEyMTVcIixcInV1aWRcIjpcIjNhYjk0ZjExLWZiNzEtNDQwMS04MDQzLTVlOTExMjI3MDM3ZVwiLFwicGF5bWVudFR5cGVcIjpcIlNJTkdMRVwiLFwibG9jYWxlXCI6XCJldFwifSIsInBheW1lbnRfc3RhdHVzIjoiUEFJRCIsImlhdCI6MTcxOTkwMzYxNiwiZXhwIjoxNzIwNTA4NDE2fQ.nH7G_SY7LimfW2HOgEViINFW2Qjk1XXZKP7xmR_Oj6c"
}
