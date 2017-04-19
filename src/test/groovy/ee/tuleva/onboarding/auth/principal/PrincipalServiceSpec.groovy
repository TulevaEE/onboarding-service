package ee.tuleva.onboarding.auth.principal

import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserRepository
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException
import spock.lang.Specification

class PrincipalServiceSpec extends Specification {

    UserRepository repository = Mock(UserRepository)
    PrincipalService service = new PrincipalService(repository)

    User sampleUser = User.builder()
            .active(true)
            .build()

    def "getFromPerson: initialising from person works" () {
        given:
        Person person = PersonFixture.samplePerson()

        1 * repository.findByPersonalCode(person.personalCode) >> sampleUser

        when:
        AuthenticatedPerson authenticatedPerson = service.getFrom(person)

        then:
        authenticatedPerson.user == sampleUser
    }

    def "getFromPerson: initialising non user works throws exception" () {
        given:
        Person person = PersonFixture.samplePerson()

        1 * repository.findByPersonalCode(person.personalCode) >> null

        when:
        AuthenticatedPerson authenticatedPerson = service.getFrom(person)

        then:
        thrown InvalidRequestException
    }

    def "getFromPerson: initialising non active user exceptions" () {
        given:
        Person person = PersonFixture.samplePerson()

        1 * repository.findByPersonalCode(person.personalCode) >> User.builder().active(false).build()

        when:
        AuthenticatedPerson authenticatedPerson = service.getFrom(person)

        then:
        thrown InvalidRequestException
    }

}
