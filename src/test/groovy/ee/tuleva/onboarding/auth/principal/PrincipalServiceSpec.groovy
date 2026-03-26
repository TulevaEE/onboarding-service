package ee.tuleva.onboarding.auth.principal

import ee.tuleva.onboarding.auth.role.Role
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.role.RoleType.*

class PrincipalServiceSpec extends Specification {

  UserService userService = Mock(UserService)
  PrincipalService service = new PrincipalService(userService)

  User sampleUser = User.builder()
      .firstName("John")
      .lastName("Doe")
      .active(true)
      .build()

  def "getFromPerson: initialising from person works"() {
    given:
    Person person = samplePerson()

    1 * userService.findByPersonalCode(person.personalCode) >> Optional.ofNullable(sampleUser)

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Map.of())

    then:
    authenticatedPerson.userId == sampleUser.id
    authenticatedPerson.firstName == sampleUser.firstName
    authenticatedPerson.lastName == sampleUser.lastName
    authenticatedPerson.personalCode == person.personalCode
  }

  def "getFromPerson: create a new user when one is not present"() {
    given:
    def person = samplePerson()
    String firstNameUncapitalized = "JOHN"
    String firstNameCorrectlyCapitalized = "John"
    String lastNameUncapitalized = "DOE"
    String lastNameCorrectlyCapitalized = "Doe"
    person = person.toBuilder()
        .firstName(firstNameUncapitalized)
        .lastName(lastNameUncapitalized)
        .build()

    1 * userService.findByPersonalCode(person.personalCode) >> Optional.empty()

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Map.of())

    then:
    1 * userService.createNewUser({ User user ->
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

  def "withRole returns person with new role preserving all other fields"() {
    given:
    def original = sampleAuthenticatedPersonAndMember().build()
    def company = new Role(LEGAL_ENTITY, "12345678", "Test Company")

    when:
    def result = service.withRole(original, company)

    then:
    result.personalCode == original.personalCode
    result.firstName == original.firstName
    result.lastName == original.lastName
    result.userId == original.userId
    result.attributes == original.attributes
    result.role == company
  }

  def "getFromPerson: uses capitalized name from database not raw auth provider name"() {
    given:
    def person = samplePerson().toBuilder()
        .firstName("JOHN")
        .lastName("DOE")
        .build()
    def user = User.builder()
        .id(1L)
        .firstName("John")
        .lastName("Doe")
        .active(true)
        .build()
    1 * userService.findByPersonalCode(person.personalCode) >> Optional.of(user)

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Map.of())

    then:
    authenticatedPerson.firstName == "John"
    authenticatedPerson.lastName == "Doe"
    authenticatedPerson.fullName == "John Doe"
  }

  def "getFromPerson: role name is capitalized even when auth provider gives all caps"() {
    given:
    def person = samplePerson().toBuilder()
        .firstName("JOHN")
        .lastName("DOE")
        .build()
    def user = User.builder()
        .id(1L)
        .firstName("John")
        .lastName("Doe")
        .personalCode(person.personalCode)
        .active(true)
        .build()
    1 * userService.findByPersonalCode(person.personalCode) >> Optional.of(user)

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Map.of())

    then:
    authenticatedPerson.role.name() == "John Doe"
  }

  def "getFromPerson: initialising non active user throws exception"() {
    given:
    Person person = samplePerson()
    User user = User.builder().active(false).build()

    1 * userService.findByPersonalCode(person.personalCode) >> Optional.ofNullable(user)

    when:
    service.getFrom(person, Map.of())

    then:
    thrown IllegalStateException
  }

}
