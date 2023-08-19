package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.aml.exception.AmlChecksMissingException
import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.idcard.IdCardSession
import ee.tuleva.onboarding.epis.contact.ContactDetailsService
import ee.tuleva.onboarding.epis.contact.event.ContactDetailsUpdatedEvent
import ee.tuleva.onboarding.mandate.event.BeforeMandateCreatedEvent
import ee.tuleva.onboarding.user.UserService
import org.springframework.security.core.Authentication
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class AmlAutoCheckerSpec extends Specification {

    AmlService amlService = Mock()
    UserService userService = Mock()
    ContactDetailsService contactDetailsService = Mock()
    AmlAutoChecker amlAutoChecker = new AmlAutoChecker(amlService, userService, contactDetailsService)

    def "checks user before login"() {
        given:
        def user = sampleUser().build()
        def person = sampleAuthenticatedPersonAndMember()
        .attributes(Map.of(IdCardSession.ID_DOCUMENT_TYPE_ATTRIBUTE, ESTONIAN_CITIZEN_ID_CARD))
            .build()
        1 * userService.findByPersonalCode(person.personalCode) >> Optional.of(user)

        when:
        amlAutoChecker.beforeLogin(new BeforeTokenGrantedEvent(this, person, GrantType.ID_CARD))

        then:
        1 * amlService.checkUserBeforeLogin(user, person, ESTONIAN_CITIZEN_ID_CARD.isResident())
    }

    def "throws exception when user not found"() {
        given:
        def person = sampleAuthenticatedPersonAndMember()
            .attributes(Map.of(IdCardSession.ID_DOCUMENT_TYPE_ATTRIBUTE, ESTONIAN_CITIZEN_ID_CARD))
            .build()
        1 * userService.findByPersonalCode(person.personalCode) >> Optional.empty()

        when:
        amlAutoChecker.beforeLogin(new BeforeTokenGrantedEvent(this, person, GrantType.MOBILE_ID))

        then:
        thrown(IllegalStateException)
        0 * amlService.checkUserBeforeLogin(*_)
    }

    def "adds pension registry name check if missing on login"() {
        given:
        def user = sampleUser().build()
        def contactDetails = contactDetailsFixture()
        def jwtToken = "token"
        1 * userService.findByPersonalCode(user.personalCode) >> Optional.of(user)
        1 * contactDetailsService.getContactDetails(user, jwtToken) >> contactDetails

        when:
        amlAutoChecker.afterLogin(new AfterTokenGrantedEvent(this, user, jwtToken))

        then:
        1 * amlService.addPensionRegistryNameCheckIfMissing(user, contactDetails)
    }

    def "adds contact details check if missing"() {
        given:
        def user = sampleUser().build()
        def contactDetails = contactDetailsFixture()

        when:
        amlAutoChecker.contactDetailsUpdated(new ContactDetailsUpdatedEvent(this, user, contactDetails))

        then:
        1 * amlService.addContactDetailsCheckIfMissing(user)
    }

    @Unroll
    def "throws exception when not all checks passed on mandate creation"() {
        given:
        def user = sampleUser().build()
        def mandate = sampleMandate()
        1 * amlService.allChecksPassed(user, mandate.getPillar()) >> allChecksPassed

        when:
        amlAutoChecker.beforeMandateCreated(new BeforeMandateCreatedEvent(this, user, mandate))
        throw new NoExceptionThrown()

        then:
        thrown(expectedException)

        where:
        allChecksPassed | expectedException
        true            | NoExceptionThrown
        false           | AmlChecksMissingException
    }

    private class NoExceptionThrown extends RuntimeException {}
}
