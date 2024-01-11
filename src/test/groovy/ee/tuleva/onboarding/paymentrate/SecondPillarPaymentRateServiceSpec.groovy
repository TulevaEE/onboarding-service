package ee.tuleva.onboarding.paymentrate

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import spock.lang.Specification

import java.time.ZoneId

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.samplePendingPaymentRateApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleCompletedPaymentRateApplicationDto

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

  def "getPendingSecondPillarPaymentRate returns rate of first matching pending application"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        ApplicationDTO sampleApplication = samplePendingPaymentRateApplicationDto()
        episService.getApplications(authenticatedPerson) >> [sampleApplication]

    when:
        BigDecimal rate = service.getPendingSecondPillarPaymentRate(authenticatedPerson)

    then:
        rate == sampleApplication.getPaymentRate()
  }

  def "getPaymentRates returns default rate when no applications pending match and default rate for current"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        episService.getApplications(authenticatedPerson) >> []

    when:
        PaymentRates rates = service.getPaymentRates(authenticatedPerson)

    then:
        rates.current == 2
        rates.pending == 2
  }

  def "getPaymentRates returns rate of first pending matching application and default rate for current"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        ApplicationDTO sampleApplication = samplePendingPaymentRateApplicationDto()
        episService.getApplications(authenticatedPerson) >> [sampleApplication]

    when:
        PaymentRates rates = service.getPaymentRates(authenticatedPerson)

    then:
        rates.current == 2
        rates.pending == sampleApplication.getPaymentRate()
  }

  def "getPaymentRates returns rate of first pending matching application and latest completed matching for current"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        ApplicationDTO sampleApplication = samplePendingPaymentRateApplicationDto()
        ApplicationDTO sampleLatestCompletedApplication = sampleCompletedPaymentRateApplicationDto()
        sampleLatestCompletedApplication.paymentRate = 4
        ApplicationDTO sampleEarlierCompletedApplication = sampleCompletedPaymentRateApplicationDto()
        sampleEarlierCompletedApplication.date = sampleLatestCompletedApplication
            .date.atZone(ZoneId.systemDefault()).minusYears(1).toInstant()
        sampleEarlierCompletedApplication.paymentRate = 6

        ApplicationDTO sampleEarlierCompletedApplication2 = sampleCompletedPaymentRateApplicationDto()
        sampleEarlierCompletedApplication2.date = sampleLatestCompletedApplication
            .date.atZone(ZoneId.systemDefault()).minusYears(2).toInstant()
        sampleEarlierCompletedApplication2.paymentRate = 6

        episService.getApplications(authenticatedPerson) >> [
            sampleEarlierCompletedApplication, sampleApplication,
            sampleLatestCompletedApplication, sampleEarlierCompletedApplication2]

    when:
        PaymentRates rates = service.getPaymentRates(authenticatedPerson)

    then:
        rates.current == sampleLatestCompletedApplication.getPaymentRate()
        rates.pending == sampleApplication.getPaymentRate()
  }

}
