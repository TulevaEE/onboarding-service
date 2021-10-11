package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.ClockFixture
import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.deadline.MandateDeadlinesFixture.sampleDeadlines
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleEarlyWithdrawalApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleTransferApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleWithdrawalApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.samplePikTransferApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL

class ApplicationServiceSpec extends Specification {

  EpisService episService = Mock()
  LocaleService localeService = Mock()
  FundRepository fundRepository = Mock()
  MandateDeadlinesService mandateDeadlinesService = Mock()

  ApplicationService applicationService =
    new ApplicationService(episService, localeService, fundRepository, mandateDeadlinesService)

  def "gets applications"() {
    given:
    def transferApplication1 = sampleTransferApplicationDto()
    def completedTransferApplication = sampleTransferApplicationDto()
    completedTransferApplication.status = COMPLETE
    completedTransferApplication.id = 456L
    def transferApplication2 = sampleTransferApplicationDto()
    def withdrawalApplication = sampleWithdrawalApplicationDto()
    def earlyWithdrawalApplication = sampleEarlyWithdrawalApplicationDto()
    def pikTransferApplication = samplePikTransferApplicationDto()
    episService.getApplications(samplePerson()) >> [
      transferApplication1, transferApplication2, completedTransferApplication,
      pikTransferApplication, withdrawalApplication, earlyWithdrawalApplication
    ]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    fundRepository.findByIsin("source") >> sampleFunds().first()
    fundRepository.findByIsin("target") >> sampleFunds().drop(1).first()

    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()

    when:
    List<Application> applications = applicationService.getApplications(samplePerson())

    then:
    applications.size() == 6
    with(applications[0]) {
      id == 456L
      type == TRANSFER
      status == COMPLETE
      creationTime == ClockFixture.now
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges.size() == 1
        with(exchanges[0]) {
          sourceFund.isin == "AE123232334"
          targetFund.isin == "EE3600109443"
          amount == 1.0
        }
        fulfillmentDate == LocalDate.parse("2021-05-03")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
    with(applications[1]) {
      id == 123L
      type == TRANSFER
      status == PENDING
      creationTime == ClockFixture.now
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges.size() == 1
        with(exchanges[0]) {
          sourceFund.isin == "AE123232334"
          targetFund.isin == "EE3600109443"
          amount == 1.0
        }
        fulfillmentDate == LocalDate.parse("2021-05-03")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
    with(applications[2]) {
      id == 123L
      type == TRANSFER
      status == PENDING
      creationTime == ClockFixture.now
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges.size() == 1
        with(exchanges[0]) {
          sourceFund.isin == "AE123232334"
          targetFund.isin == "EE3600109443"
          amount == 1.0
        }
        fulfillmentDate == LocalDate.parse("2021-05-03")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
    with(applications[3]) {
      id == 123L
      type == TRANSFER
      status == PENDING
      creationTime == ClockFixture.now
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges.size() == 1
        with(exchanges[0]) {
          sourceFund.isin == "AE123232334"
          targetFund == null
          targetPik == "EE801281685311741971"
          amount == 1.0
        }
        fulfillmentDate == LocalDate.parse("2021-05-03")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
    with(applications[4]) {
      id == 123L
      type == EARLY_WITHDRAWAL
      status == PENDING
      creationTime == ClockFixture.now
      with(details) {
        depositAccountIBAN == "IBAN"
        fulfillmentDate == LocalDate.parse("2021-09-01")
        cancellationDeadline == Instant.parse("2021-07-31T20:59:59.999999999Z")
      }
    }
    with(applications[5]) {
      id == 123L
      type == WITHDRAWAL
      status == PENDING
      creationTime == ClockFixture.now
      with(details) {
        depositAccountIBAN == "IBAN"
        fulfillmentDate == LocalDate.parse("2021-04-16")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
  }

  def "checks if there is a pending withdrawal"() {
    given:
    episService.getApplications(samplePerson()) >> [sampleWithdrawalApplicationDto()]
    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    when:
    def hasPendingWithdrawals = applicationService.hasPendingWithdrawals(samplePerson())
    then:
    hasPendingWithdrawals
  }
}
