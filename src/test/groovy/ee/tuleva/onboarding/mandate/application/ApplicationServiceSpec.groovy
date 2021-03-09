package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.mandate.cancellation.MandateCancellationService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleApplicationDto

class ApplicationServiceSpec extends Specification {

    EpisService episService = Mock()
    ApplicationConverter applicationConverter = new ApplicationConverter()
    MandateCancellationService mandateCancellationService = Mock()

    ApplicationService applicationService =
        new ApplicationService(episService, applicationConverter, mandateCancellationService)

    def "gets applications"() {
        given:
        def applicationDTO = sampleApplicationDto()
        episService.getApplications(samplePerson()) >> [applicationDTO]

        when:
        List<Application> applications = applicationService.get(samplePerson())

        then:
        applications == [applicationConverter.convert(applicationDTO)]
    }

    def "can cancel applications"() {
        given:
        def person = samplePerson()
        def user = sampleUser().build()
        def applicationDTO = sampleApplicationDto()
        def mandate = sampleMandate()

        1 * episService.getApplications(person) >> [applicationDTO]
        1 * mandateCancellationService.saveCancellationMandate(user.id, applicationDTO.type) >> mandate

        when:
        ApplicationCancellationResponse response =
            applicationService.createCancellationMandate(person, user.id, applicationDTO.id)

        then:
        response.mandateId == mandate.id
    }
}
