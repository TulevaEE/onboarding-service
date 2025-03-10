package ee.tuleva.onboarding.mandate.payment.rate

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.generic.GenericMandateService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class PaymentRateServiceSpec extends Specification {

  GenericMandateService genericMandateService = Mock(GenericMandateService)

  PaymentRateService paymentRateService = new PaymentRateService(
      genericMandateService)


  def "can change payment rate"() {
    given:
    BigDecimal paymentRate = new BigDecimal("2.0")
    AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()

    Mandate mandate = sampleMandate()

    1 * genericMandateService.createGenericMandate(authenticatedPerson, { it -> it.getDetails().getPaymentRate().getNumericValue() == paymentRate }) >> mandate

    when:
    Mandate result = paymentRateService.savePaymentRateMandate(authenticatedPerson, paymentRate)

    then:
    result.id == mandate.id
  }

}
