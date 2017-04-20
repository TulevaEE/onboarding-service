package ee.tuleva.onboarding.auth.principal

import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserRepository
import org.apache.commons.lang3.text.WordUtils
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
        authenticatedPerson.firstName == person.firstName
        authenticatedPerson.lastName == person.lastName
        authenticatedPerson.personalCode == person.personalCode
    }

    def "getFromPerson: create a new user when one is not present" () {
        given:
        Person person = PersonFixture.samplePerson()
        person.firstName = "JORDAN"

        1 * repository.findByPersonalCode(person.personalCode) >> null

        when:
        AuthenticatedPerson authenticatedPerson = service.getFrom(person)

        then:
        1 * repository.save({User user ->
            user.firstName == WordUtils.capitalizeFully(person.firstName) &&
                    user.lastName == WordUtils.capitalizeFully(person.lastName) &&
                    user.personalCode == person.personalCode &&
                    user.active == true
        }) >> User.builder()
                .personalCode(person.personalCode)
                .firstName(WordUtils.capitalizeFully(person.firstName))
                .lastName(WordUtils.capitalizeFully(person.lastName))
                .id(123)
                .active(true)
                .build()

        authenticatedPerson.user.firstName == WordUtils.capitalizeFully(person.firstName)
        authenticatedPerson.user.lastName == WordUtils.capitalizeFully(person.lastName)
        authenticatedPerson.user.personalCode == person.personalCode
        authenticatedPerson.user.active == true

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
