package ee.tuleva.onboarding.payment

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import org.junit.jupiter.api.BeforeAll
import spock.lang.Specification

class PaymentInternalReferenceServiceSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  PaymentInternalReferenceService paymentInternalReferenceService;

  @BeforeAll
  def setup() {
    paymentInternalReferenceService = new PaymentInternalReferenceService(objectMapper)
  }

  def "Creates a correct payment reference"() {
    when:
    String reference = paymentInternalReferenceService.getPaymentReference(sampleAuthenticatedPerson)
    then:
    reference.startsWith("""{"personalCode":"38501010000","uuid":""")
    reference.length() == 76
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()
}
