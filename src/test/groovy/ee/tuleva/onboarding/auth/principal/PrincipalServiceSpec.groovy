package ee.tuleva.onboarding.auth.principal

import ee.tuleva.onboarding.auth.role.Role
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.principal.PrincipalUsers.PrincipalUser
import static ee.tuleva.onboarding.auth.role.RoleType.*

class PrincipalServiceSpec extends Specification {

  Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC)
  PrincipalUsers principalUsers = Mock(PrincipalUsers)
  PrincipalService service = new PrincipalService(principalUsers, false, clock)

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

  def "getFromPerson: a person who turned 18 today can self-authenticate"() {
    given:
    def onTheir18thBirthday = new PrincipalService(principalUsers, false,
        Clock.fixed(Instant.parse("2006-12-12T00:00:00Z"), ZoneOffset.UTC))
    Person person = samplePerson()
    1 * principalUsers.findOrCreate(person) >> samplePrincipalUser

    when:
    AuthenticatedPerson authenticatedPerson = onTheir18thBirthday.getFrom(person, Map.of())

    then:
    authenticatedPerson.personalCode == person.personalCode
  }

  def "self-service is refused to a minor until the clock's date reaches their 18th birthday"() {
    given:
    Person minor = samplePerson().toBuilder().personalCode("61506150006").build()
    def ownRole = new Role(PERSON, minor.personalCode, "Mari Maasikas")
    def service = new PrincipalService(principalUsers, false, Clock.fixed(Instant.parse(now), ZoneOffset.UTC))

    expect:
    service.refusesSelfServiceTo(minor, ownRole) == refused

    where:
    now                    | refused
    "2033-06-14T23:59:59Z" | true
    "2033-06-15T00:00:00Z" | false
  }

  def "getFromPerson: a minor cannot self-authenticate while minor self-service is disabled"() {
    given:
    Person minor = samplePerson().toBuilder().personalCode("61506150006").build()

    when:
    service.getFrom(minor, Map.of())

    then:
    thrown(MinorCannotSelfAuthenticateException)
    0 * principalUsers.findOrCreate(_)
  }

  def "getFromPerson: a minor self-authenticates as themselves once minor self-service is enabled"() {
    given:
    def serviceWithMinorSelfService = new PrincipalService(principalUsers, true, clock)
    Person minor = samplePerson().toBuilder().personalCode("61506150006").build()
    1 * principalUsers.findOrCreate(minor) >> samplePrincipalUser

    when:
    AuthenticatedPerson authenticatedPerson = serviceWithMinorSelfService.getFrom(minor, Map.of())

    then:
    authenticatedPerson.personalCode == minor.personalCode
    authenticatedPerson.role == new Role(PERSON, minor.personalCode, "Jordan Valdma")
    authenticatedPerson.actingAsSelf
  }

  def "getFromPerson: an adult self-authenticates the same way whether minor self-service is enabled or not"() {
    given:
    def serviceWithMinorSelfService = new PrincipalService(principalUsers, minorSelfServiceEnabled, clock)
    Person adult = samplePerson()
    1 * principalUsers.findOrCreate(adult) >> samplePrincipalUser

    when:
    AuthenticatedPerson authenticatedPerson = serviceWithMinorSelfService.getFrom(adult, Map.of())

    then:
    authenticatedPerson.personalCode == adult.personalCode
    authenticatedPerson.actingAsSelf

    where:
    minorSelfServiceEnabled << [false, true]
  }

  def "withRole into a minor child role is not blocked by the self-auth gate"() {
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

  def "rebuilding a principal from a token is not blocked by the self-auth gate"() {
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

  def "self-service is refused to a minor acting as themselves while minor self-service is disabled"() {
    given:
    Person minor = samplePerson().toBuilder().personalCode("61506150006").build()

    when:
    def refused = service.refusesSelfServiceTo(minor, new Role(PERSON, minor.personalCode, "Mari Maasikas"))

    then:
    refused
    0 * principalUsers._
  }

  def "self-service is not refused to a minor acting as themselves while minor self-service is enabled"() {
    given:
    def serviceWithMinorSelfService = new PrincipalService(principalUsers, true, clock)
    Person minor = samplePerson().toBuilder().personalCode("61506150006").build()

    expect:
    !serviceWithMinorSelfService.refusesSelfServiceTo(minor, new Role(PERSON, minor.personalCode, "Mari Maasikas"))
  }

  def "self-service is not refused to a parent acting as their minor child"() {
    expect:
    !service.refusesSelfServiceTo(samplePerson(), new Role(PERSON, "61506150006", "Mari Maasikas"))
  }

  def "self-service is not refused to an adult acting as themselves"() {
    given:
    Person adult = samplePerson()

    expect:
    !service.refusesSelfServiceTo(adult, new Role(PERSON, adult.personalCode, "Jordan Valdma"))
  }

  def "a minor with no role in their token counts as acting as themselves"() {
    given:
    Person minor = samplePerson().toBuilder().personalCode("61506150006").build()

    expect:
    service.refusesSelfServiceTo(minor, null)
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
