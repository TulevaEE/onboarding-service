package ee.tuleva.onboarding.user


import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.idcard.IdCardSession
import ee.tuleva.onboarding.auth.idcard.IdDocumentType
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.PersonImpl

class UserDetailsUpdaterSpec extends Specification {

    UserService userService = Mock(UserService)

    UserDetailsUpdater service = new UserDetailsUpdater(userService)

    def "OnBeforeTokenGrantedEvent: Update user details on before token granted event"(IdDocumentType documentType, Boolean resident) {
        given:

        Person samplePerson = new PersonImpl(
            personalCode: "38512121215",
            firstName: "ERKO",
            lastName: "RISTHEIN"
        )

        OAuth2Authentication oAuth2Authentication = Mock({
            getPrincipal() >> samplePerson
            getCredentials() >> IdCardSession.builder()
                .firstName("ERKO")
                .lastName("RISTHEIN")
                .documentType(documentType)
                .build()
        })

        BeforeTokenGrantedEvent beforeTokenGrantedEvent = new BeforeTokenGrantedEvent(this, oAuth2Authentication)

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
                user.lastName == "Risthein" &&
                user.resident == resident
        })

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

    def "OnBeforeTokenGrantedEvent: Does notchange resident if not ID card login"() {
        given:

        Person samplePerson = new PersonImpl(
            personalCode: "38512121215",
            firstName: "ERKO",
            lastName: "RISTHEIN"
        )

        OAuth2Authentication oAuth2Authentication = Mock({
            getPrincipal() >> samplePerson
            getCredentials() >> null
        })

        BeforeTokenGrantedEvent beforeTokenGrantedEvent = new BeforeTokenGrantedEvent(this, oAuth2Authentication)

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
                user.lastName == "Risthein" &&
                user.resident == null
        })
    }
}
