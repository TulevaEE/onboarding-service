package ee.tuleva.onboarding.payment.provider

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.locale.LocaleService
import groovy.json.JsonSlurper
import spock.lang.Specification

import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData

class PaymentInternalReferenceServiceSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  PaymentInternalReferenceService paymentInternalReferenceService
  LocaleService localeService = Mock()

  def setup() {
    paymentInternalReferenceService = new PaymentInternalReferenceService(objectMapper, localeService)
  }

  def "Creates a correct payment reference"() {
    when:
    1 * localeService.currentLocale >> Locale.ENGLISH
    String referenceString = paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson, aPaymentData)
    def slurper = new JsonSlurper()
    def reference = slurper.parseText(referenceString)
    then:
    reference.personalCode == "38501010000"
    reference.locale == "en"
    reference.uuid.size() == 36
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()
}
