package ee.tuleva.onboarding.auth.principal

import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
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
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Map.of())

    then:
    authenticatedPerson.userId == sampleUser.id
    authenticatedPerson.firstName == person.firstName
    authenticatedPerson.lastName == person.lastName
    authenticatedPerson.personalCode == person.personalCode
  }

  def "getFromPerson: create a new user when one is not present"() {
    given:
    def person = samplePerson()
    String firstNameUncapitalized = "JORDAN"
    String firstNameCorrectlyCapitalized = "Jordan"
    String lastNameUncapitalized = "VALDMA"
    String lastNameCorrectlyCapitalized = "Valdma"
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

  def "withActingAs returns person with new actingAs preserving all other fields"() {
    given:
    def original = AuthenticatedPerson.builder()
        .personalCode("38501010000")
        .firstName("Jordan")
        .lastName("Valdma")
        .userId(1L)
        .attributes(Map.of("key", "value"))
        .actingAs(new ActingAs.Person("38501010000"))
        .build()
    def company = new ActingAs.Company("12345678")

    when:
    def result = service.withActingAs(original, company)

    then:
    result.personalCode == "38501010000"
    result.firstName == "Jordan"
    result.lastName == "Valdma"
    result.userId == 1L
    result.attributes == Map.of("key", "value")
    result.actingAs == company
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
