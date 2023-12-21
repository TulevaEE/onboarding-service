package ee.tuleva.onboarding.mandate.application


import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.mandate.cancellation.MandateCancellationService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleTransferApplicationDto

class ApplicationCancellationServiceSpec extends Specification {
  EpisService episService = Mock(EpisService)
  MandateCancellationService mandateCancellationService = Mock(MandateCancellationService)
  ApplicationCancellationService applicationCancellationService = new ApplicationCancellationService(mandateCancellationService, episService)

  def "can cancel applications"() {
    given:
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def applicationDTO = sampleTransferApplicationDto()
    def mandate = sampleMandate()

    1 * episService.getApplications(person) >> [applicationDTO]
    1 * mandateCancellationService.saveCancellationMandate(person, _) >> mandate

    when:
    ApplicationCancellationResponse response =
      applicationCancellationService.createCancellationMandate(person, applicationDTO.id)

    then:
    response.mandateId == mandate.id
  }

  def "returns first application when multiple applications with same id found"() {
    given:
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def applicationDTO = sampleTransferApplicationDto()
    def mandate = sampleMandate()

    1 * episService.getApplications(person) >> [applicationDTO, applicationDTO]
    1 * mandateCancellationService.saveCancellationMandate(person, _) >> mandate

    when:
    ApplicationCancellationResponse response =
      applicationCancellationService.createCancellationMandate(person, applicationDTO.id)

    then:
    response.mandateId == mandate.id
  }
}
