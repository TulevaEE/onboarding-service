package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.mandate.cancellation.MandateCancellationService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleTransferApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleWithdrawalApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.withdrawalApplication

class ApplicationServiceSpec extends Specification {

  EpisService episService = Mock()
  MandateCancellationService mandateCancellationService = Mock()
  LocaleService localeService = Mock()
  FundRepository fundRepository = Mock()

  ApplicationService applicationService =
    new ApplicationService(episService, mandateCancellationService, localeService, fundRepository)

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

    when:
    List<Application> applications = applicationService.get(samplePerson())

    then:
    applications.size() == 3
    applications[0].id == 456L
    applications[0].type == ApplicationType.TRANSFER
    applications[0].status == ApplicationStatus.COMPLETE
    applications[0].details.sourceFund.isin == "AE123232334"
    applications[0].details.exchanges.size() == 1
    applications[0].details.exchanges[0].targetFund.isin == "EE3600109443"
    applications[0].details.exchanges[0].amount == BigDecimal.ONE
    applications[1].id == 123L
    applications[1].type == ApplicationType.TRANSFER
    applications[1].status == ApplicationStatus.PENDING
    applications[1].details.sourceFund.isin == "AE123232334"
    applications[1].details.exchanges.size() == 2
    applications[1].details.exchanges[0].targetFund.isin == "EE3600109443"
    applications[1].details.exchanges[0].amount == BigDecimal.ONE
    applications[1].details.exchanges[1].targetFund.isin == "EE3600109443"
    applications[1].details.exchanges[1].amount == BigDecimal.ONE
    applications[2] == withdrawalApplication().build()
  }

  def "can cancel applications"() {
    given:
    def person = samplePerson()
    def user = sampleUser().build()
    def applicationDTO = sampleTransferApplicationDto()
    def mandate = sampleMandate()

    1 * episService.getApplications(person) >> [applicationDTO]
    1 * mandateCancellationService.saveCancellationMandate(user.id, _) >> mandate

    when:
    ApplicationCancellationResponse response =
      applicationService.createCancellationMandate(person, user.id, applicationDTO.id)

    then:
    response.mandateId == mandate.id
  }
}
