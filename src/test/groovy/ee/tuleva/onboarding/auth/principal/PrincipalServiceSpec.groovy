package ee.tuleva.onboarding.auth.principal

import ee.tuleva.onboarding.auth.role.Role
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.principal.PrincipalUsers.PrincipalUser
import static ee.tuleva.onboarding.auth.role.RoleType.*

class PrincipalServiceSpec extends Specification {

  Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC)
  PrincipalUsers principalUsers = Mock(PrincipalUsers)
  PrincipalService service = new PrincipalService(principalUsers)

  PrincipalUser samplePrincipalUser = new PrincipalUser(1L, true)

  def "getFromPerson: initialising from person works"() {
    given:
    Person person = samplePerson()

    1 * principalUsers.findOrCreate(person) >> samplePrincipalUser

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Map.of())

    then:
    authenticatedPerson.userId == samplePrincipalUser.id()
    authenticatedPerson.firstName == person.firstName
    authenticatedPerson.lastName == person.lastName
    authenticatedPerson.personalCode == person.personalCode
  }

  def "getFromPerson: creates a new user when one is not present"() {
    given:
    def person = samplePerson().toBuilder()
        .firstName("JOHN")
        .lastName("DOE")
        .build()

    1 * principalUsers.findOrCreate(person) >> new PrincipalUser(123L, true)

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Map.of())

    then:
    authenticatedPerson.userId == 123L
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

  def "getFromPerson: uses capitalized name from the auth provider, not the stored user name"() {
    given:
    def person = samplePerson().toBuilder()
        .firstName("JAAK")
        .lastName("KUUSK-ÕUNAPUU")
        .build()
    1 * principalUsers.findOrCreate(person) >> samplePrincipalUser

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Map.of())

    then:
    authenticatedPerson.firstName == "Jaak"
    authenticatedPerson.lastName == "Kuusk-Õunapuu"
    authenticatedPerson.fullName == "Jaak Kuusk-Õunapuu"
  }

  def "getFromPerson: role name is capitalized even when auth provider gives all caps"() {
    given:
    def person = samplePerson().toBuilder()
        .firstName("JOHN")
        .lastName("DOE")
        .build()
    1 * principalUsers.findOrCreate(person) >> samplePrincipalUser

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(person, Map.of())

    then:
    authenticatedPerson.role.name() == "John Doe"
  }

  def "getFromPerson: a minor self-authenticates as themselves"() {
    given:
    Person minor = samplePerson().toBuilder().personalCode("61506150006").build()
    1 * principalUsers.findOrCreate(minor) >> samplePrincipalUser

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(minor, Map.of())

    then:
    authenticatedPerson.personalCode == minor.personalCode
    authenticatedPerson.role == new Role(PERSON, minor.personalCode, "Jordan Valdma")
    authenticatedPerson.isMinorActingAsSelf(LocalDate.now(clock))
  }

  def "getFromPerson: an adult self-authenticates as themselves"() {
    given:
    Person adult = samplePerson()
    1 * principalUsers.findOrCreate(adult) >> samplePrincipalUser

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(adult, Map.of())

    then:
    authenticatedPerson.personalCode == adult.personalCode
    authenticatedPerson.actingAsSelf
    !authenticatedPerson.isMinorActingAsSelf(LocalDate.now(clock))
  }

  def "withRole switches a parent into their minor child's role without looking anyone up"() {
    given:
    def parent = sampleAuthenticatedPersonAndMember().build()
    def childRole = new Role(PERSON, "61506150006", "Mari Maasikas")

    when:
    def result = service.withRole(parent, childRole)

    then:
    result.role == childRole
    result.personalCode == parent.personalCode
    0 * principalUsers._
  }

  def "rebuilding a minor's principal from a token keeps the role the token carries"() {
    given:
    Person minor = samplePerson().toBuilder().personalCode("61506150006").build()
    def role = new Role(PERSON, minor.personalCode, "Mari Maasikas")
    1 * principalUsers.findOrCreate(minor) >> samplePrincipalUser

    when:
    AuthenticatedPerson authenticatedPerson = service.getFrom(minor, Map.of(), role)

    then:
    authenticatedPerson.personalCode == minor.personalCode
    authenticatedPerson.role == role
  }

  def "getFromPerson: initialising non active user throws exception"() {
    given:
    Person person = samplePerson()

    1 * principalUsers.findOrCreate(person) >> new PrincipalUser(1L, false)

    when:
    service.getFrom(person, Map.of())

    then:
    thrown IllegalStateException
  }

}
