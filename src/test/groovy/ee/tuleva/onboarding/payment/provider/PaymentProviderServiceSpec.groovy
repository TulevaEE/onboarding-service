package ee.tuleva.onboarding.payment.provider

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.locale.MockLocaleService
import ee.tuleva.onboarding.payment.PaymentLink
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentDataForMemberPayment
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentDataWithoutAnAmount
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*
import static java.time.ZoneOffset.UTC

class PaymentProviderServiceSpec extends Specification {

  Clock clock = Clock.fixed(Instant.parse("2020-11-23T10:00:00Z"), UTC)

  PaymentInternalReferenceService paymentInternalReferenceService = Mock()
  LocaleService localeService = new MockLocaleService("et")
  PaymentProviderService paymentLinkService

  void setup() {
    paymentLinkService = new PaymentProviderService(
        clock,
        paymentInternalReferenceService,
        aPaymentProviderConfiguration(),
        localeService
    )
    paymentLinkService.paymentProviderUrl = "https://sandbox-payments.montonio.com"
    paymentLinkService.apiUrl = "https://onboarding-service.tuleva.ee/v1"
    paymentLinkService.memberFee = new BigDecimal(125)
  }

  void beforeEach() {

  }

  /* Flaky test: the jwt token uses a HashMap which does not guarantee order, so the jwt payload might change */
  def "can get a payment link"() {
    given:
    String internalReference = anInternalReferenceSerialized
    1 * paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson, aPaymentData) >> internalReference
    when:
    PaymentLink paymentLink = paymentLinkService.getPaymentLink(aPaymentData, sampleAuthenticatedPerson)

    then:
    paymentLink.url() == "https://sandbox-payments.montonio.com?payment_token=" + aSerializedPaymentProviderToken
  }

  /* Flaky test: the jwt token uses a HashMap which does not guarantee order, so the jwt payload might change */
  def "can get a member fee payment link"() {
    given:
    String internalReference = anInternalReferenceSerialized
    1 * paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson, aPaymentDataForMemberPayment) >> internalReference
    when:
    PaymentLink paymentLink = paymentLinkService.getPaymentLink(aPaymentDataForMemberPayment, sampleAuthenticatedPerson)

    then:
    paymentLink.url() == "https://sandbox-payments.montonio.com?payment_token=" + aSerializedPaymentProviderTokenForMemberFeePayment
  }

  /* Flaky test: the jwt token uses a HashMap which does not guarantee order, so the jwt payload might change */
  def "can get a member fee test payment link"() {
    given:
    String internalReference = anInternalReferenceSerialized
    String testPersonalCode = "38888888888"
    paymentLinkService.memberFeeTestPersonalCode = testPersonalCode;
    def testPaymentData = aPaymentDataForMemberPayment;
    testPaymentData.recipientPersonalCode = testPersonalCode
    1 * paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson, testPaymentData) >> internalReference
    when:
    PaymentLink paymentLink = paymentLinkService.getPaymentLink(testPaymentData, sampleAuthenticatedPerson)

    then:
    paymentLink.url() == "https://sandbox-payments.montonio.com?payment_token=eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL21lbWJlci1zdWNjZXNzIiwiYW1vdW50IjoxLCJwYXltZW50X2luZm9ybWF0aW9uX3Vuc3RydWN0dXJlZCI6Im1lbWJlcjozODg4ODg4ODg4OCIsImNoZWNrb3V0X2ZpcnN0X25hbWUiOiJKb3JkYW4iLCJtZXJjaGFudF9ub3RpZmljYXRpb25fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL25vdGlmaWNhdGlvbiIsInByZXNlbGVjdGVkX2FzcHNwIjoiZXhhbXBsZUFzcHNwIiwibWVyY2hhbnRfcmVmZXJlbmNlIjoie1wicGVyc29uYWxDb2RlXCI6IFwiMzg4MTIxMjEyMTVcIiwgXCJyZWNpcGllbnRQZXJzb25hbENvZGVcIjogXCIzODgxMjEyMTIxNVwiLCBcInV1aWRcIjogXCIzYWI5NGYxMS1mYjcxLTQ0MDEtODA0My01ZTkxMTIyNzAzN2VcIiwgXCJwYXltZW50VHlwZVwiOiBcIk1FTUJFUl9GRUVcIn0iLCJhY2Nlc3Nfa2V5IjoiZXhhbXBsZUFjY2Vzc0tleVR1bHVuZHVzdWhpc3R1IiwiY3VycmVuY3kiOiJFVVIiLCJleHAiOjE2MDYxMjYyMDAsInByZXNlbGVjdGVkX2xvY2FsZSI6ImV0IiwiY2hlY2tvdXRfbGFzdF9uYW1lIjoiVmFsZG1hIn0.nj9t7g-Ddno6s92_JFBXzuFOZLsFbtqvXmyAnd-WN2s"
  }

  def "can not get a member fee payment link when fee is not set"() {
    def originalFee = paymentLinkService.memberFee
    given:
    paymentLinkService.memberFee = null
    when:
    paymentLinkService.getPaymentLink(aPaymentDataForMemberPayment, sampleAuthenticatedPerson)

    then:
    thrown(IllegalArgumentException)

    cleanup:
    paymentLinkService.memberFee = originalFee
  }

  def "can not get a payment link when anmount is not set"() {
    when:
    paymentLinkService.getPaymentLink(aPaymentDataWithoutAnAmount, sampleAuthenticatedPerson)

    then:
    thrown(IllegalArgumentException)
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
