package ee.tuleva.onboarding.mandate.cancellation

import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.mandate.MandateService
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.sampleApplication
import static ee.tuleva.onboarding.mandate.application.ApplicationType.SELECTION
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL

class MandateCancellationServiceSpec extends Specification {

    MandateService mandateService = Mock()
    UserService userService = Mock()
    EpisService episService = Mock()
    UserConversionService conversionService = Mock()
    CancellationMandateBuilder cancellationMandateBuilder = Mock()

    MandateCancellationService mandateCancellationService

    def setup() {
        mandateCancellationService = new MandateCancellationService(
            mandateService, userService, episService, conversionService, cancellationMandateBuilder
        )
    }

    def "saves cancellation mandate"() {
        given:
        def user = sampleUser().build()
        def conversion = fullyConverted()
        def contactDetails = contactDetailsFixture()
        def applicationToCancel = sampleApplication().build()
        def mandate = sampleMandate()

        1 * userService.getById(user.id) >> user
        1 * conversionService.getConversion(user) >> conversion
        1 * episService.getContactDetails(user) >> contactDetails
        1 * cancellationMandateBuilder.build(applicationToCancel, user, conversion, contactDetails) >> mandate

        when:
        mandateCancellationService.saveCancellationMandate(user.id, applicationToCancel)

        then:
        1 * mandateService.save(user, mandate)
    }

    def "validates application type before saving"() {
        given:
        def user = sampleUser().build()
        def applicationToCancel = sampleApplication().type(SELECTION).build()

        when:
        mandateCancellationService.saveCancellationMandate(user.id, applicationToCancel)

        then:
        thrown(InvalidApplicationTypeException)
    }
}
