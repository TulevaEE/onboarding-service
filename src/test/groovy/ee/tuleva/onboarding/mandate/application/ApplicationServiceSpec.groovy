package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.deadline.MandateDeadlinesFixture.sampleDeadlines
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleTransferApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleWithdrawalApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.withdrawalApplication

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
    completedTransferApplication.status = ApplicationStatus.COMPLETE
    completedTransferApplication.id = 456L
    def transferApplication2 = sampleTransferApplicationDto()
    def withdrawalApplication1 = sampleWithdrawalApplicationDto()
    episService.getApplications(samplePerson()) >> [transferApplication1, transferApplication2, completedTransferApplication, withdrawalApplication1]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    fundRepository.findByIsin("source") >> sampleFunds().first()
    fundRepository.findByIsin("target") >> sampleFunds().drop(1).first()

    mandateDeadlinesService.deadlines >> sampleDeadlines()

    when:
    List<Application> applications = applicationService.getApplications(samplePerson())

    then:
    applications.size() == 3
    applications[0].id == 456L
    applications[0].type == ApplicationType.TRANSFER
    applications[0].status == ApplicationStatus.COMPLETE
    applications[0].details.sourceFund.isin == "AE123232334"
    applications[0].details.cancellationDeadline == LocalDate.parse("2021-03-31")
    applications[0].details.fulfillmentDate == LocalDate.parse("2021-05-03")
    applications[0].details.exchanges.size() == 1
    applications[0].details.exchanges[0].targetFund.isin == "EE3600109443"
    applications[0].details.exchanges[0].amount == BigDecimal.ONE
    applications[1].id == 123L
    applications[1].type == ApplicationType.TRANSFER
    applications[1].status == ApplicationStatus.PENDING
    applications[1].details.sourceFund.isin == "AE123232334"
    applications[1].details.cancellationDeadline == LocalDate.parse("2021-03-31")
    applications[1].details.fulfillmentDate == LocalDate.parse("2021-05-03")
    applications[1].details.exchanges.size() == 2
    applications[1].details.exchanges[0].targetFund.isin == "EE3600109443"
    applications[1].details.exchanges[0].amount == BigDecimal.ONE
    applications[1].details.exchanges[1].targetFund.isin == "EE3600109443"
    applications[1].details.exchanges[1].amount == BigDecimal.ONE
    applications[2] == withdrawalApplication().build()
  }

  def "checks if there is a pending withdrawal"() {
    given:
    def withdrawalApplication1 = sampleWithdrawalApplicationDto()
    episService.getApplications(samplePerson()) >> [withdrawalApplication1]
    when:
    Boolean result = applicationService.hasPendingWithdrawals(samplePerson())
    then:
    result
  }
}
