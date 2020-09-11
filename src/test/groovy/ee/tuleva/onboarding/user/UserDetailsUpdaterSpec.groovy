package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.aml.AmlCheck
import ee.tuleva.onboarding.aml.AmlService
import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.idcard.IdCardSession
import ee.tuleva.onboarding.auth.idcard.IdDocumentType
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.user.event.BeforeUserCreatedEvent
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

import static ee.tuleva.onboarding.aml.AmlCheckType.RESIDENCY_AUTO
import static ee.tuleva.onboarding.auth.PersonFixture.PersonImpl
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

class UserDetailsUpdaterSpec extends Specification {

    UserService userService = Mock()
    AmlService amlService = Mock()
    EpisService episService = Mock()

    UserDetailsUpdater service = new UserDetailsUpdater(userService, amlService, episService)

    def "OnBeforeTokenGrantedEvent: Update user details on before token granted event"(IdDocumentType documentType, Boolean resident) {
        given:

        Person samplePerson = new PersonImpl(
            personalCode: "38512121215",
            firstName: "ERKO",
            lastName: "RISTHEIN"
        )

        Authentication auth = Mock({
            getCredentials() >> IdCardSession.builder()
                .firstName("ERKO")
                .lastName("RISTHEIN")
                .documentType(documentType)
                .build()
        })

        OAuth2Authentication oAuth2Authentication = Mock({
            getPrincipal() >> samplePerson
            getUserAuthentication() >> auth
        })

        BeforeTokenGrantedEvent beforeTokenGrantedEvent = new BeforeTokenGrantedEvent(this, oAuth2Authentication, GrantType.ID_CARD)

        when:
        service.onBeforeTokenGrantedEvent(beforeTokenGrantedEvent)

        then:
        1 * userService.findByPersonalCode(samplePerson.personalCode) >> Optional.of(
            User.builder()
                .firstName("this will change")
                .lastName("this will also change")
                .build()
        )

        1 * userService.save({ User user ->
            user.firstName == "Erko" &&
                user.lastName == "Risthein"
        })

        if (resident != null) {
            1 * amlService.addCheckIfMissing({ AmlCheck check ->
                check.type == RESIDENCY_AUTO &&
                    check.success == resident
            })
        }

        where:
        documentType                                                 | resident
        IdDocumentType.ESTONIAN_CITIZEN_ID_CARD                      | true
        IdDocumentType.OLD_ID_CARD                                   | true
        IdDocumentType.EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD | false
        IdDocumentType.E_RESIDENT_DIGITAL_ID_CARD                    | false
        IdDocumentType.EUROPEAN_CITIZEN_ID_CARD                      | false
        IdDocumentType.DIPLOMATIC_ID_CARD                            | false
        IdDocumentType.DIGITAL_ID_CARD                               | null
        IdDocumentType.OLD_DIGITAL_ID_CARD                           | null
        IdDocumentType.UNKNOWN                                       | null

    }

    def "OnBeforeTokenGrantedEvent: Does not change resident if not ID card login"() {
        given:

        Person samplePerson = new PersonImpl(
            personalCode: "38512121215",
            firstName: "ERKO",
            lastName: "RISTHEIN"
        )

        OAuth2Authentication oAuth2Authentication = Mock({
            getPrincipal() >> samplePerson
            getUserAuthentication() >> Mock(Authentication)
        })

        BeforeTokenGrantedEvent beforeTokenGrantedEvent = new BeforeTokenGrantedEvent(this, oAuth2Authentication, GrantType.ID_CARD)

        when:
        service.onBeforeTokenGrantedEvent(beforeTokenGrantedEvent)

        then:
        1 * userService.findByPersonalCode(samplePerson.personalCode) >> Optional.of(
            User.builder()
                .firstName("this will change")
                .lastName("this will also change")
                .build()
        )

        1 * userService.save({ User user ->
            user.firstName == "Erko" &&
                user.lastName == "Risthein"
        })
    }

    def "updates user email and phone number based on epis info"() {
        given:
        def user = sampleUser().email(null).phoneNumber(null).build()
        def contactDetails = contactDetailsFixture()
        1 * episService.getContactDetails(user) >> contactDetails

        when:
        service.onBeforeUserCreatedEvent(new BeforeUserCreatedEvent(user))

        then:
        user.phoneNumber == contactDetails.phoneNumber
        user.email == contactDetails.email
    }
}
