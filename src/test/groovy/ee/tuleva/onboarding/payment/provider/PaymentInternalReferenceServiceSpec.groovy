package ee.tuleva.onboarding.payment.provider

import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.payment.PaymentData
import groovy.json.JsonSlurper
import spock.lang.Specification

import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SAVINGS
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData

class PaymentInternalReferenceServiceSpec extends Specification {

  JsonMapper objectMapper = JsonMapper.builder().build()

  PaymentInternalReferenceService paymentInternalReferenceService
  LocaleService localeService = Mock()

  def setup() {
    paymentInternalReferenceService = new PaymentInternalReferenceService(objectMapper, localeService)
  }

  def "Creates a correct payment reference for personal code"() {
    when:
    1 * localeService.currentLocale >> Locale.ENGLISH
    String referenceString = paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson, aPaymentData, "description")
    def slurper = new JsonSlurper()
    def reference = slurper.parseText(referenceString)
    then:
    reference.personalCode == "38501010000"
    reference.locale == "en"
    reference.uuid.size() == 36
    reference.paymentType == aPaymentData.getType().toString()
    reference.description == "description"
    reference.recipientPartyType == "PERSON"
  }

  def "Creates a correct payment reference for registry code"() {
    given:
    def companyPaymentData = PaymentData.builder()
        .recipientPersonalCode("12345678")
        .amount(100.00)
        .currency(EUR)
        .type(SAVINGS)
        .paymentChannel(LHV)
        .build()

    when:
    1 * localeService.currentLocale >> Locale.ENGLISH
    String referenceString = paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson, companyPaymentData, "description")
    def slurper = new JsonSlurper()
    def reference = slurper.parseText(referenceString)

    then:
    reference.personalCode == "38501010000"
    reference.recipientPersonalCode == "12345678"
    reference.recipientPartyType == "LEGAL_ENTITY"
    reference.paymentType == "SAVINGS"
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()
}
