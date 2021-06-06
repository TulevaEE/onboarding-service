package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.deadline.MandateDeadlinesFixture.sampleDeadlines
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleTransferApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleWithdrawalApplicationDto
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
    transferApplication2.fundTransferExchanges = null
    def withdrawalApplication1 = sampleWithdrawalApplicationDto()
    episService.getApplications(samplePerson()) >> [transferApplication1, transferApplication2, completedTransferApplication, withdrawalApplication1]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    fundRepository.findByIsin("source") >> sampleFunds().first()
    fundRepository.findByIsin("target") >> sampleFunds().drop(1).first()

    mandateDeadlinesService.deadlines >> sampleDeadlines()

    when:
    List<Application> applications = applicationService.getApplications(samplePerson())

    then:
    applications.size() == 4
    with(applications[0]) {
      id == 456L
      type == TRANSFER
      status == COMPLETE
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges.size() == 1
        exchanges[0].targetFund.isin == "EE3600109443"
        exchanges[0].amount == 1.0
      }
    }
    with(applications[1]) {
      id == 123L
      type == TRANSFER
      status == PENDING
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges[0].targetFund.isin == "EE3600109443"
        exchanges[0].amount == 1.0
      }
    }
    with(applications[2]) {
      id == 123L
      type == TRANSFER
      status == PENDING
      details == null
    }
    with(applications[3]) {
      id == 123L
      type == WITHDRAWAL
      status == PENDING
      details.depositAccountIBAN == "IBAN"
    }
  }

  def "checks if there is a pending withdrawal"() {
    given:
    def withdrawalApplication1 = sampleWithdrawalApplicationDto()
    episService.getApplications(samplePerson()) >> [withdrawalApplication1]
    mandateDeadlinesService.deadlines >> sampleDeadlines()
    when:
    Boolean result = applicationService.hasPendingWithdrawals(samplePerson())
    then:
    result
  }
}
