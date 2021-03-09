package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import spock.lang.Specification

class ApplicationServiceSpec extends Specification {
    EpisService episService = Mock(EpisService)
    ApplicationConverter applicationConverter = Mock(ApplicationConverter)
    ApplicationService applicationService = new ApplicationService(episService, applicationConverter)

    def "Converts applications"() {
        given:
        ApplicationDTO applicationDTO = ApplicationDTO.builder().build()
        Application application = Application.builder().build()
        _ * episService.getApplications(PersonFixture.samplePerson()) >> [applicationDTO]
        _ * applicationConverter.convert(applicationDTO, 'et') >> application
        when:
        List<Application> applications = applicationService.get(PersonFixture.samplePerson(), 'et')
        then:
        applications == [application]
    }
}
