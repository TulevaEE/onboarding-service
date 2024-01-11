package ee.tuleva.onboarding.paymentrate

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.mandate.application.Application
import ee.tuleva.onboarding.mandate.application.ApplicationService
import spock.lang.Specification

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember

import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.*

class SecondPillarPaymentRateServiceSpec extends Specification {

  def applicationService = Mock(ApplicationService)
  def service = new SecondPillarPaymentRateService(applicationService)

  def "getPendingSecondPillarPaymentRate returns default rate when no applications match"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        applicationService.getPaymentRateApplications(authenticatedPerson) >> []

    when:
        BigDecimal rate = service.getPendingSecondPillarPaymentRate(authenticatedPerson)

    then:
        rate == new BigDecimal(2)
  }

  def "getPendingSecondPillarPaymentRate returns rate of first matching pending application"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        Application sampleApplication = samplePendingPaymentRateApplication()
        applicationService.getPaymentRateApplications(authenticatedPerson) >> [sampleApplication]

    when:
        BigDecimal rate = service.getPendingSecondPillarPaymentRate(authenticatedPerson)

    then:
        rate == sampleApplication.getDetails().getPaymentRate()
  }

  def "getPaymentRates returns rate of first pending matching application and latest completed matching for current"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        Application samplePendingApplication = samplePendingPaymentRateApplication()
        Application sampleLatestCompletedApplication = sampleCompletedPaymentRateApplication(BigDecimal.valueOf(4))

        Instant oneYearBefore = ZonedDateTime.ofInstant(sampleLatestCompletedApplication.getCreationTime(), ZoneId.systemDefault())
            .minusYears(1).toInstant()
        Application sampleEarlierCompletedApplication = sampleCompletedPaymentRateApplication(BigDecimal.valueOf(2), oneYearBefore)

        Instant twoYearsBefore = ZonedDateTime.ofInstant(sampleLatestCompletedApplication.getCreationTime(), ZoneId.systemDefault())
            .minusYears(2).toInstant()
        Application sampleEarlierCompletedApplication2 = sampleCompletedPaymentRateApplication(BigDecimal.valueOf(2), twoYearsBefore)

        applicationService.getPaymentRateApplications(authenticatedPerson) >> [
            sampleEarlierCompletedApplication, samplePendingApplication,
            sampleLatestCompletedApplication, sampleEarlierCompletedApplication2
        ]

    when:
        PaymentRates rates = service.getPaymentRates(authenticatedPerson)

    then:
        rates.current == sampleLatestCompletedApplication.getDetails().getPaymentRate()
        rates.pending == samplePendingApplication.getDetails().getPaymentRate()
  }


  def "getPaymentRates returns default rate when no applications pending match and default rate for current"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        applicationService.getPaymentRateApplications(authenticatedPerson) >> []

    when:
        PaymentRates rates = service.getPaymentRates(authenticatedPerson)

    then:
        rates.current == 2
        rates.pending == 2
  }

  def "getPaymentRates returns rate of first pending matching application and default rate for current"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        Application sampleApplication = samplePendingPaymentRateApplication()
        applicationService.getPaymentRateApplications(authenticatedPerson) >> [sampleApplication]

    when:
        PaymentRates rates = service.getPaymentRates(authenticatedPerson)

    then:
        rates.current == 2
        rates.pending == sampleApplication.getDetails().getPaymentRate()
  }

}
