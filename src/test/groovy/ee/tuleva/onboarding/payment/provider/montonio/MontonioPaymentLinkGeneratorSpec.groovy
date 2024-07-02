package ee.tuleva.onboarding.payment.provider.montonio

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.payment.PaymentLink
import spock.lang.Specification

import static ee.tuleva.onboarding.payment.PaymentFixture.aMontonioOrder
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData

class MontonioPaymentLinkGeneratorSpec extends Specification {


  def montonioOrderCreator = Mock(MontonioOrderCreator)
  def monotonioOrderClient = Mock(MontonioOrderClient)

  def montonioPaymentLinkGenerator = new MontonioPaymentLinkGenerator(montonioOrderCreator, monotonioOrderClient)

  def "can get a payment link"() {
    def aPaymentUrl = "https://payment.url"

    given:
    1 * montonioOrderCreator.getOrder(aPaymentData(), sampleAuthenticatedPerson) >> aMontonioOrder
    1 * monotonioOrderClient.getPaymentUrl(aMontonioOrder, aPaymentData()) >> aPaymentUrl

    when:
    PaymentLink paymentLink = montonioPaymentLinkGenerator.getPaymentLink(aPaymentData, sampleAuthenticatedPerson)

    then:
    paymentLink.url() == "https://payment.url"
  }

/*
  def "can get a member fee payment link"() {
    given:
    String internalReference = anInternalReferenceSerialized
    1 * paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson, PaymentFixture.aPaymentDataForMemberPayment) >> internalReference
    when:
    PaymentLink paymentLink = paymentLinkService.getPaymentLink(PaymentFixture.aPaymentDataForMemberPayment, sampleAuthenticatedPerson)

    then:
    paymentLink.url() == "https://sandbox-payments.montonio.com?payment_token=" + aSerializedPaymentProviderTokenForMemberFeePayment
  }


  def "can get a member fee test payment link"() {
    given:
    String internalReference = anInternalReferenceSerialized
    String testPersonalCode = "38888888888"
    paymentLinkService.memberFeeTestPersonalCode = testPersonalCode
    def testPaymentData = PaymentFixture.aPaymentDataForMemberPayment
    testPaymentData.recipientPersonalCode = testPersonalCode
    1 * paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson, testPaymentData) >> internalReference
    when:
    PaymentLink paymentLink = paymentLinkService.getPaymentLink(testPaymentData, sampleAuthenticatedPerson)

    then:
    paymentLink.url() == "https://sandbox-payments.montonio.com?payment_token=eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL21lbWJlci1zdWNjZXNzIiwiYW1vdW50IjoxLCJwcmVzZWxlY3RlZF9hc3BzcCI6ImV4YW1wbGVBc3BzcCIsIm1lcmNoYW50X3JlZmVyZW5jZSI6IntcInBlcnNvbmFsQ29kZVwiOiBcIjM4ODEyMTIxMjE1XCIsIFwicmVjaXBpZW50UGVyc29uYWxDb2RlXCI6IFwiMzg4MTIxMjEyMTVcIiwgXCJ1dWlkXCI6IFwiM2FiOTRmMTEtZmI3MS00NDAxLTgwNDMtNWU5MTEyMjcwMzdlXCIsIFwicGF5bWVudFR5cGVcIjogXCJNRU1CRVJfRkVFXCJ9IiwicGF5bWVudF9pbmZvcm1hdGlvbl91bnN0cnVjdHVyZWQiOiJtZW1iZXI6Mzg4ODg4ODg4ODgiLCJhY2Nlc3Nfa2V5IjoiZXhhbXBsZUFjY2Vzc0tleVR1bHVuZHVzdWhpc3R1IiwiY2hlY2tvdXRfZmlyc3RfbmFtZSI6IkpvcmRhbiIsImN1cnJlbmN5IjoiRVVSIiwibWVyY2hhbnRfbm90aWZpY2F0aW9uX3VybCI6Imh0dHBzOi8vb25ib2FyZGluZy1zZXJ2aWNlLnR1bGV2YS5lZS92MS9wYXltZW50cy9ub3RpZmljYXRpb24iLCJleHAiOjE2MDYxMjYyMDAsInByZXNlbGVjdGVkX2xvY2FsZSI6ImV0IiwiY2hlY2tvdXRfbGFzdF9uYW1lIjoiVmFsZG1hIn0.1TKabI6LQ6rHAh6KUP6I0h8NkhL2rWT21uS5nA-M1U8"
  }

  def "can not get a member fee payment link when fee is not set"() {
    def originalFee = paymentLinkService.memberFee
    given:
    paymentLinkService.memberFee = null
    when:
    paymentLinkService.getPaymentLink(PaymentFixture.aPaymentDataForMemberPayment, sampleAuthenticatedPerson)

    then:
    thrown(IllegalArgumentException)

    cleanup:
    paymentLinkService.memberFee = originalFee
  }

  def "can not get a payment link when anmount is not set"() {
    when:
    paymentLinkService.getPaymentLink(PaymentFixture.aPaymentDataWithoutAnAmount, sampleAuthenticatedPerson)

    then:
    thrown(IllegalArgumentException)
  }*/

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
