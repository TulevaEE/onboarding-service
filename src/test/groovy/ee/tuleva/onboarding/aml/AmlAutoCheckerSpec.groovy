package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.aml.exception.AmlChecksMissingException
import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.AuthenticationTokens
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.epis.contact.ContactDetailsService
import ee.tuleva.onboarding.epis.contact.event.ContactDetailsUpdatedEvent
import ee.tuleva.onboarding.mandate.event.BeforeMandateCreatedEvent
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.GrantType.MOBILE_ID
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE
import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate

class AmlAutoCheckerSpec extends Specification {

    AmlService amlService = Mock()
    UserService userService = Mock()
    ContactDetailsService contactDetailsService = Mock()
    AmlAutoChecker amlAutoChecker = new AmlAutoChecker(amlService, userService, contactDetailsService)

    def "checks user before login"() {
        given:
        def user = sampleUser().build()
        def person = sampleAuthenticatedPersonAndMember()
        .attributes(Map.of(ID_DOCUMENT_TYPE, ESTONIAN_CITIZEN_ID_CARD.name()))
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
            .attributes(Map.of(ID_DOCUMENT_TYPE, ESTONIAN_CITIZEN_ID_CARD.name()))
            .build()
        1 * userService.findByPersonalCode(person.personalCode) >> Optional.empty()

        when:
        amlAutoChecker.beforeLogin(new BeforeTokenGrantedEvent(this, person, MOBILE_ID))

        then:
        thrown(IllegalStateException)
        0 * amlService.checkUserBeforeLogin(*_)
    }

    def "adds pension registry name check if missing on login"() {
        given:
        def user = sampleUser().build()
        def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        def grantType = MOBILE_ID
        def contactDetails = contactDetailsFixture()
        def tokens = new AuthenticationTokens("access token", "refresh token")
        1 * userService.findByPersonalCode(authenticatedPerson.personalCode) >> Optional.of(user)
        1 * contactDetailsService.getContactDetails(authenticatedPerson, tokens.accessToken()) >> contactDetails

        when:
        amlAutoChecker.afterLogin(new AfterTokenGrantedEvent(this, authenticatedPerson, grantType, tokens))

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

  def "adds pep and sanction checks on 3rd pillar mandates"() {
    given:
    def user = sampleUser().build()
    def mandate = thirdPillarMandate()
    1 * amlService.addSanctionAndPepCheckIfMissing(user, mandate.address)
    1 * amlService.allChecksPassed(user, mandate.getPillar()) >> true

    when:
    amlAutoChecker.beforeMandateCreated(new BeforeMandateCreatedEvent(this, user, mandate))

    then:
    noExceptionThrown()
  }

    private class NoExceptionThrown extends RuntimeException {}
}
