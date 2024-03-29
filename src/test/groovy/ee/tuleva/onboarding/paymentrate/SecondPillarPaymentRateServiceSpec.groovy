package ee.tuleva.onboarding.paymentrate

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.mandate.application.Application
import ee.tuleva.onboarding.mandate.application.ApplicationService
import spock.lang.Specification

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.sampleCompletedPaymentRateApplication
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.samplePendingPaymentRateApplication

class SecondPillarPaymentRateServiceSpec extends Specification {

  def applicationService = Mock(ApplicationService)
  def service = new SecondPillarPaymentRateService(applicationService)

  def "getPaymentRates returns rate of first pending matching application and latest completed matching for current"() {
    given:
        Person person = samplePerson()
        Application samplePendingApplication = samplePendingPaymentRateApplication()
        Application sampleLatestCompletedApplication = sampleCompletedPaymentRateApplication(BigDecimal.valueOf(4))

        Instant oneYearBefore = ZonedDateTime.ofInstant(sampleLatestCompletedApplication.getCreationTime(), ZoneOffset.UTC)
            .minusYears(1).toInstant()
        Application sampleEarlierCompletedApplication = sampleCompletedPaymentRateApplication(BigDecimal.valueOf(2), oneYearBefore)

        Instant twoYearsBefore = ZonedDateTime.ofInstant(sampleLatestCompletedApplication.getCreationTime(), ZoneOffset.UTC)
            .minusYears(2).toInstant()
        Application sampleEarlierCompletedApplication2 = sampleCompletedPaymentRateApplication(BigDecimal.valueOf(2), twoYearsBefore)

        applicationService.getPaymentRateApplications(person) >> [
            sampleEarlierCompletedApplication, samplePendingApplication,
            sampleLatestCompletedApplication, sampleEarlierCompletedApplication2
        ]

    when:
        PaymentRates rates = service.getPaymentRates(person)

    then:
        rates.current == sampleLatestCompletedApplication.getDetails().getPaymentRate()
        rates.pending.get() == samplePendingApplication.getDetails().getPaymentRate()
  }


  def "getPaymentRates returns default rate when no applications pending match and default rate for current"() {
    given:
        Person person = samplePerson()
        applicationService.getPaymentRateApplications(person) >> []

    when:
        PaymentRates rates = service.getPaymentRates(person)

    then:
        rates.current == 2
        rates.pending == Optional.empty()
  }

  def "getPaymentRates returns rate of first pending matching application and default rate for current"() {
    given:
        Person person = samplePerson()
        Application sampleApplication = samplePendingPaymentRateApplication()
        applicationService.getPaymentRateApplications(person) >> [sampleApplication]

    when:
        PaymentRates rates = service.getPaymentRates(person)

    then:
        rates.current == 2
        rates.pending.get() == sampleApplication.getDetails().getPaymentRate()
  }

}
