package ee.tuleva.onboarding.payment.provider

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.locale.MockLocaleService
import ee.tuleva.onboarding.payment.PaymentLink
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData
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

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
