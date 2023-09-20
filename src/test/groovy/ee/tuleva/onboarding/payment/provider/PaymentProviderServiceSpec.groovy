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

  def "can get a member fee payment link"() {
    given:
    String internalReference = anInternalReferenceSerialized
    1 * paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson, aPaymentDataForMemberPayment) >> internalReference
    when:
    PaymentLink paymentLink = paymentLinkService.getPaymentLink(aPaymentDataForMemberPayment, sampleAuthenticatedPerson)

    then:
    paymentLink.url() == "https://sandbox-payments.montonio.com?payment_token=" + aSerializedPaymentProviderTokenForMemberFeePayment
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
