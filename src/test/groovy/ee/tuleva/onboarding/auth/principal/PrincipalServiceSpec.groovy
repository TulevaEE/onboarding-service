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
        authenticatedPerson.firstName == person.firstName
        authenticatedPerson.lastName == person.lastName
        authenticatedPerson.personalCode == person.personalCode
    }

    def "getFromPerson: create a new user when one is not present" () {
        given:
        Person person = PersonFixture.samplePerson()
        String firstNameUncapitalized = "JORDAN"
        String firstNameCorrectlyCapitalized = "Jordan"
        person.firstName = firstNameUncapitalized
        String lastNameUncapitalized = "VALDMA"
        String lastNameCorrectlyCapitalized = "Valdma"
        person.lastName = lastNameUncapitalized


        1 * repository.findByPersonalCode(person.personalCode) >> null

        when:
        AuthenticatedPerson authenticatedPerson = service.getFrom(person)

        then:
        1 * repository.save({User user ->
            user.firstName == firstNameCorrectlyCapitalized &&
                    user.lastName == lastNameCorrectlyCapitalized &&
                    user.personalCode == person.personalCode &&
                    user.active
        }) >> User.builder()
                .personalCode(person.personalCode)
                .firstName(firstNameCorrectlyCapitalized)
                .lastName(lastNameCorrectlyCapitalized)
                .id(123)
                .active(true)
                .build()

        authenticatedPerson.user.firstName == firstNameCorrectlyCapitalized
        authenticatedPerson.user.lastName == lastNameCorrectlyCapitalized
        authenticatedPerson.user.personalCode == person.personalCode
        authenticatedPerson.user.active

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
