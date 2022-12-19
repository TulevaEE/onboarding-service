package ee.tuleva.onboarding.payment.provider

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.locale.MockLocaleService
import ee.tuleva.onboarding.payment.PaymentData
import ee.tuleva.onboarding.payment.PaymentLink
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.payment.PaymentData.Bank.LHV
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*
import static java.time.ZoneOffset.UTC

class PaymentProviderServiceSpec extends Specification {

  Clock clock = Clock.fixed(Instant.parse("2020-11-23T10:00:00Z"), UTC)

  EpisService episService = Mock()
  PaymentInternalReferenceService paymentInternalReferenceService = Mock()
  LocaleService localeService = new MockLocaleService("et")
  PaymentProviderService paymentLinkService

  void setup() {
    paymentLinkService = new PaymentProviderService(
        clock,
        episService,
        paymentInternalReferenceService,
        aPaymentProviderConfiguration(),
        localeService
    )
    paymentLinkService.paymentProviderUrl = "https://sandbox-payments.montonio.com"
    paymentLinkService.apiUrl = "https://onboarding-service.tuleva.ee/v1"
  }

  def "can get a payment link"() {
    given:
    String internalReference = anInternalReferenceSerialized
    PaymentData paymentData = PaymentData.builder()
        .currency(EUR)
        .amount(BigDecimal.TEN)
        .type(SINGLE)
        .bank(LHV)
        .build()

    1 * paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson as Person) >> internalReference
    1 * episService.getContactDetails(sampleAuthenticatedPerson) >> contactDetailsFixture()
    when:
    PaymentLink paymentLink = paymentLinkService.getPaymentLink(paymentData, sampleAuthenticatedPerson)

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
