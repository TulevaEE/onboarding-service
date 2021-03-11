package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.mandate.cancellation.MandateCancellationService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleTransferApplicationDto

class ApplicationCancellationServiceSpec extends Specification {
  EpisService episService = Mock(EpisService)
  MandateCancellationService mandateCancellationService = Mock(MandateCancellationService)
  ApplicationCancellationService applicationCancellationService = new ApplicationCancellationService(mandateCancellationService, episService)

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
      applicationCancellationService.createCancellationMandate(person, user.id, applicationDTO.id)

    then:
    response.mandateId == mandate.id
  }
}
