package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

class UserNameUpdaterSpec extends Specification {

    UserService userService = Mock(UserService)

    UserNameUpdater service = new UserNameUpdater(userService)

    def "OnBeforeTokenGrantedEvent: Update user name on before token granted event"() {
        given:

        Person samplePerson = PersonFixture.samplePerson()

        OAuth2Authentication oAuth2Authentication = Mock({
            getPrincipal() >> samplePerson
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
            user.firstName == samplePerson.firstName && user.lastName == samplePerson.lastName})

    }
}
