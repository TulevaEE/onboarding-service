package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.error.NotFoundException
import ee.tuleva.onboarding.mandate.MandateGateway
import ee.tuleva.onboarding.mandate.MandateService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationSnapshotFixture.sampleTransferApplicationDto

class ApplicationCancellationServiceSpec extends Specification {
  MandateGateway mandateGateway = Mock(MandateGateway)
  MandateService mandateService = Mock(MandateService)
  ApplicationCancellationService applicationCancellationService = new ApplicationCancellationService(mandateService, mandateGateway)

  def "can cancel applications"() {
    given:
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def applicationDTO = sampleTransferApplicationDto()
    def mandate = sampleMandate()

    1 * mandateGateway.getApplications(person) >> [applicationDTO]
    1 * mandateService.saveCancellation(person, applicationDTO) >> mandate

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

    1 * mandateGateway.getApplications(person) >> [applicationDTO, applicationDTO]
    1 * mandateService.saveCancellation(person, applicationDTO) >> mandate

    when:
    ApplicationCancellationResponse response =
      applicationCancellationService.createCancellationMandate(person, applicationDTO.id)

    then:
    response.mandateId == mandate.id
  }

  def "throws NotFoundException when no application matches the given id"() {
    given:
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def applicationDTO = sampleTransferApplicationDto()

    1 * mandateGateway.getApplications(person) >> [applicationDTO]

    when:
    applicationCancellationService.createCancellationMandate(person, 999999L)

    then:
    thrown(NotFoundException)
  }
}
