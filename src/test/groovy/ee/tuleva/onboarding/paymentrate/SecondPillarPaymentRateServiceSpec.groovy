package ee.tuleva.onboarding.paymentrate

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.samplePaymentRateApplicationDto

class SecondPillarPaymentRateServiceSpec extends Specification {

  def episService = Mock(EpisService)

  def service = new SecondPillarPaymentRateService(episService)

  def "getPendingSecondPillarPaymentRate returns default rate when no applications match"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        episService.getApplications(authenticatedPerson) >> []

    when:
        BigDecimal rate = service.getPendingSecondPillarPaymentRate(authenticatedPerson)

    then:
        rate == new BigDecimal(2)
  }

  def "getPendingSecondPillarPaymentRate returns rate of first matching application"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        ApplicationDTO sampleApplication = samplePaymentRateApplicationDto()
        episService.getApplications(authenticatedPerson) >> [sampleApplication]

    when:
        BigDecimal rate = service.getPendingSecondPillarPaymentRate(authenticatedPerson)

    then:
        rate == sampleApplication.getPaymentRate()
  }

  def "getPaymentRates returns default rate when no applications match and default rate for current"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        episService.getApplications(authenticatedPerson) >> []

    when:
        PaymentRates rates = service.getPaymentRates(authenticatedPerson)

    then:
        rates.current == 2
        rates.pending == 2
  }

  def "getPaymentRates returns rate of first matching application and default rate for current"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        ApplicationDTO sampleApplication = samplePaymentRateApplicationDto()
        episService.getApplications(authenticatedPerson) >> [sampleApplication]

    when:
        PaymentRates rates = service.getPaymentRates(authenticatedPerson)

    then:
        rates.current == 2
        rates.pending == sampleApplication.getPaymentRate()
  }

}
