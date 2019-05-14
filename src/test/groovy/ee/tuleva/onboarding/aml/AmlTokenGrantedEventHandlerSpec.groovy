package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.user.UserService
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class AmlTokenGrantedEventHandlerSpec extends Specification {
    AmlService amlService = Mock()
    UserService userService = Mock()
    AmlTokenGrantedEventHandler handler = new AmlTokenGrantedEventHandler(amlService, userService)

    def "checks user after login"() {
        given:
        def user = sampleUser().build()
        def person = PersonFixture.samplePerson()
        BeforeTokenGrantedEvent event = Mock({
            getAuthentication() >> Mock(OAuth2Authentication, {
                getPrincipal() >> person
            })
        })
        when:
        handler.onBeforeTokenGrantedEvent(event)
        then:
        1 * userService.findByPersonalCode(person.personalCode) >> Optional.of(user)
        1 * amlService.checkUserAfterLogin(user, person)
    }

    def "throws when user not found"() {
        given:
        def user = sampleUser().build()
        def person = PersonFixture.samplePerson()
        BeforeTokenGrantedEvent event = Mock({
            getAuthentication() >> Mock(OAuth2Authentication, {
                getPrincipal() >> person
            })
        })
        when:
        handler.onBeforeTokenGrantedEvent(event)
        then:
        thrown(IllegalStateException)
        1 * userService.findByPersonalCode(person.personalCode) >> Optional.empty()
        0 * amlService.checkUserAfterLogin(user, person)
    }
}
