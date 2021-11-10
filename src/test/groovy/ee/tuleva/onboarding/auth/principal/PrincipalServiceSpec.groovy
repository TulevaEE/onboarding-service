package ee.tuleva.onboarding.auth.principal

import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class PrincipalServiceSpec extends Specification {

  UserService userService = Mock(UserService)
  PrincipalService service = new PrincipalService(userService)

  User sampleUser = User.builder()
      .active(true)
      .build()

  def "getFromPerson: initialising from person works"() {
    given:
    Person person = samplePerson()

    1 * userService.findByPersonalCode(person.personalCode) >> Optional.ofNullable(sampleUser)

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Optional::empty)

    then:
    authenticatedPerson.userId == sampleUser.id
    authenticatedPerson.firstName == person.firstName
    authenticatedPerson.lastName == person.lastName
    authenticatedPerson.personalCode == person.personalCode
  }

  def "getFromPerson: create a new user when one is not present"() {
    given:
    Person person = samplePerson()
    String firstNameUncapitalized = "JORDAN"
    String firstNameCorrectlyCapitalized = "Jordan"
    person.firstName = firstNameUncapitalized
    String lastNameUncapitalized = "VALDMA"
    String lastNameCorrectlyCapitalized = "Valdma"
    person.lastName = lastNameUncapitalized

    1 * userService.findByPersonalCode(person.personalCode) >> Optional.empty()

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Optional::empty)

    then:
    1 * userService.createNewUser({User user ->
      user.firstName == firstNameCorrectlyCapitalized &&
          user.lastName == lastNameCorrectlyCapitalized &&
          user.personalCode == person.personalCode &&
          user.active
    }) >> User.builder()
        .id(123)
        .active(true)
        .build()

    authenticatedPerson.userId == 123

  }

  def "getFromPerson: initialising non active user throws exception"() {
    given:
    Person person = samplePerson()
    User user = User.builder().active(false).build()

    1 * userService.findByPersonalCode(person.personalCode) >> Optional.ofNullable(user)

    when:
    service.getFrom(person, Optional::empty)

    then:
    thrown InvalidRequestException
  }

}
