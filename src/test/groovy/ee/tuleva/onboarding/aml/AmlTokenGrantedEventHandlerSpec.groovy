package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.user.UserService
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class AmlTokenGrantedEventHandlerSpec extends Specification {
    AmlService amlService = Mock()
    UserService userService = Mock()
    AmlTokenGrantedEventHandler handler = new AmlTokenGrantedEventHandler(amlService, userService)

    def "checks user after login"() {
        given:
        def user = sampleUser().build()
        def person = samplePerson()
        def auth = Mock(OAuth2Authentication)
        def event = new BeforeTokenGrantedEvent(this, person, auth, GrantType.MOBILE_ID)
        when:
        handler.onBeforeTokenGrantedEvent(event)
        then:
        1 * userService.findByPersonalCode(person.personalCode) >> Optional.of(user)
        1 * amlService.checkUserAfterLogin(user, person)
    }

    def "throws when user not found"() {
        given:
        def user = sampleUser().build()
        def person = samplePerson()
        def auth = Mock(OAuth2Authentication)
        def event = new BeforeTokenGrantedEvent(this, person, auth, GrantType.MOBILE_ID)
        when:
        handler.onBeforeTokenGrantedEvent(event)
        then:
        thrown(IllegalStateException)
        1 * userService.findByPersonalCode(person.personalCode) >> Optional.empty()
        0 * amlService.checkUserAfterLogin(user, person)
    }
}
