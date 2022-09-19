package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.epis.EpisService
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.payment.PaymentProviderConfigurationFixture.aPaymentProviderBankConfiguration
import static ee.tuleva.onboarding.payment.Bank.LHV
import static PaymentFixture.aPaymentProviderBankConfiguration
import static PaymentFixture.anInternalReferenceSerialized
import static java.time.ZoneOffset.UTC

class PaymentProviderServiceSpec extends Specification {

  Clock clock = Clock.fixed(Instant.parse("2020-11-23T10:00:00Z"), UTC)

  Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurations
    = [:]

  private final EpisService episService = Mock()
  private final PaymentInternalReferenceService paymentInternalReferenceService = Mock()
  PaymentProviderService paymentLinkService

  void setup() {
    paymentProviderBankConfigurations.put(LHV.getBeanName(), samplePaymentProviderBankConfiguration())
    paymentProviderBankConfigurations.put(LHV.getBeanName(), aPaymentProviderBankConfiguration())
    paymentLinkService = new PaymentProviderService(
        clock,
        episService,
        paymentInternalReferenceService,
        paymentProviderBankConfigurations
    )
    paymentLinkService.paymentProviderUrl = "https://sandbox-payments.montonio.com"
    paymentLinkService.apiUrl = "https://onboarding-service.tuleva.ee/v1"
  }

  void create() {
    given:
    String internalReference = anInternalReferenceSerialized
    PaymentData paymentData = PaymentData.builder()
        .person(sampleAuthenticatedPerson)
        .currency(Currency.EUR)
        .amount(BigDecimal.TEN)
        .bank(Bank.LHV)
        .build()

    1 * paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson as Person) >> internalReference
    1 * episService.getContactDetails(sampleAuthenticatedPerson) >> contactDetailsFixture()
    when:
    String paymentLink = paymentLinkService.getPaymentUrl(paymentData)

    then:
    paymentLink.url() == "https://sandbox-payments.montonio.com?payment_token=eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL3N1Y2Nlc3MiLCJhbW91bnQiOjEwLCJwYXltZW50X2luZm9ybWF0aW9uX3Vuc3RydWN0dXJlZCI6IjMwMTAxMTE5ODI4IiwiY2hlY2tvdXRfZmlyc3RfbmFtZSI6IkpvcmRhbiIsIm1lcmNoYW50X25vdGlmaWNhdGlvbl91cmwiOiJodHRwczovL29uYm9hcmRpbmctc2VydmljZS50dWxldmEuZWUvdjEvcGF5bWVudHMvbm90aWZpY2F0aW9uIiwicHJlc2VsZWN0ZWRfYXNwc3AiOiJleGFtcGxlQXNwc3AiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJ7XCJwZXJzb25hbENvZGVcIjogXCIxMjM0NDM0MzRcIiwgXCJ1dWlkXCI6IFwiMjMzMlwifSIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwicGF5bWVudF9pbmZvcm1hdGlvbl9zdHJ1Y3R1cmVkIjoiOTkzNDMyNDMyIiwiY3VycmVuY3kiOiJFVVIiLCJleHAiOjE2MDYxMjYyMDAsInByZXNlbGVjdGVkX2xvY2FsZSI6ImV0IiwiY2hlY2tvdXRfbGFzdF9uYW1lIjoiVmFsZG1hIn0.O5zhG_x5Fb6a8jFFaLmPi6bCyH1b9wk5P3EOn08r3Tk"
    paymentLink == "https://sandbox-payments.montonio.com?payment_token=" + aSerializedToken
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
