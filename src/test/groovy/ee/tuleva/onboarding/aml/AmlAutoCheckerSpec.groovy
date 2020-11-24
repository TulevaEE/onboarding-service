package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.idcard.IdCardSession
import ee.tuleva.onboarding.user.UserService
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD

class AmlAutoCheckerSpec extends Specification {

    AmlService amlService = Mock()
    UserService userService = Mock()
    AmlAutoChecker handler = new AmlAutoChecker(amlService, userService)

    def "checks user after login"() {
        given:
        def user = sampleUser().build()
        def person = samplePerson()
        1 * userService.findByPersonalCode(person.personalCode) >> Optional.of(user)

        Authentication auth = Mock({
            getCredentials() >> IdCardSession.builder()
                .firstName("ERKO")
                .lastName("RISTHEIN")
                .documentType(ESTONIAN_CITIZEN_ID_CARD)
                .build()
        })

        OAuth2Authentication authentication = Mock({
            getUserAuthentication() >> auth
        })

        def event = new BeforeTokenGrantedEvent(this, person, authentication, GrantType.ID_CARD)

        when:
        handler.onBeforeTokenGrantedEvent(event)

        then:
        1 * amlService.checkUserAfterLogin(user, person, ESTONIAN_CITIZEN_ID_CARD.isResident())
    }

    def "throws exception when user not found"() {
        given:
        def person = samplePerson()
        OAuth2Authentication auth = Mock({
            getUserAuthentication() >> Mock(Authentication)
        })
        def event = new BeforeTokenGrantedEvent(this, person, auth, GrantType.MOBILE_ID)
        1 * userService.findByPersonalCode(person.personalCode) >> Optional.empty()

        when:
        handler.onBeforeTokenGrantedEvent(event)

        then:
        thrown(IllegalStateException)
        0 * amlService.checkUserAfterLogin(*_)
    }
}
