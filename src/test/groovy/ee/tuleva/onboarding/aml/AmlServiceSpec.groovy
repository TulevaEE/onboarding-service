package ee.tuleva.onboarding.aml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.BooleanNode
import ee.tuleva.onboarding.aml.sanctions.MatchResponse
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.event.TrackableEvent
import ee.tuleva.onboarding.event.TrackableEventType
import ee.tuleva.onboarding.time.ClockHolder
import ee.tuleva.onboarding.user.User
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.aml.AmlCheckType.*
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.DAYS

class AmlServiceSpec extends Specification {

  AmlCheckRepository amlCheckRepository = Mock()
  ApplicationEventPublisher eventPublisher = Mock()
  PepAndSanctionCheckService sanctionCheckService = Mock()

  ObjectMapper objectMapper = new ObjectMapper()

  Clock clock = Clock.fixed(Instant.parse("2020-11-23T10:00:00Z"), UTC)

  AmlService amlService = new AmlService(amlCheckRepository, eventPublisher, sanctionCheckService)

  def aYearAgo = Instant.now(clock).minus(365, DAYS)

  def setup() {
    ClockHolder.setClock(clock)
  }

  def cleanup() {
    ClockHolder.setDefaultClock()
  }

  def "adds aml checks before login"() {
    given:
    def user = sampleUserNonMember().build()
    def isResident = true

    when:
    amlService.checkUserBeforeLogin(user, user, isResident)
    then:
    1 * amlCheckRepository.save({ check ->
      check.personalCode == user.personalCode &&
          check.type == DOCUMENT &&
          check.success
    })
    1 * amlCheckRepository.save({ check ->
      check.personalCode == user.personalCode &&
          check.type == SK_NAME &&
          check.success
    })
    1 * amlCheckRepository.save({ check ->
      check.personalCode == user.personalCode &&
          check.type == RESIDENCY_AUTO &&
          check.success
    })
  }

  def "does not add residency check if its null"() {
    given:
    def user = sampleUserNonMember().build()
    def isResident = null

    when:
    amlService.checkUserBeforeLogin(user, user, isResident)
    then:
    2 * amlCheckRepository.save({ check ->
      check.personalCode == user.personalCode &&
          check.type != RESIDENCY_AUTO &&
          check.success
    })
  }

  def "add pension registry name check if missing"() {
    given:
    def user = sampleUserNonMember().build()
    def contactDetails = ContactDetails.builder()
        .firstName(user.firstName)
        .lastName(user.lastName)
        .personalCode(user.personalCode)
        .build()
    when:
    amlService.addPensionRegistryNameCheckIfMissing(user, contactDetails)
    then:
    1 * amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(user.personalCode, PENSION_REGISTRY_NAME, aYearAgo) >> false
    1 * amlCheckRepository.save({ check ->
      check.personalCode == user.personalCode &&
          check.type == PENSION_REGISTRY_NAME &&
          check.success
    })
  }

  def "add contact details check if missing"() {
    given:
    def user = sampleUserNonMember().build()
    when:
    amlService.addContactDetailsCheckIfMissing(user)
    then:
    1 * amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(user.personalCode, CONTACT_DETAILS, aYearAgo) >> false
    1 * amlCheckRepository.save({ check ->
      check.personalCode == user.personalCode &&
          check.type == CONTACT_DETAILS &&
          check.success
    })
  }

  def "adds check if missing"() {
    given:
    def user = sampleUserNonMember().build()
    def type = DOCUMENT
    def success = true
    def amlCheck = check(type, success, user)
    when:
    amlService.addCheckIfMissing(amlCheck)
    then:
    1 * amlCheckRepository.save({ check ->
      check.personalCode == user.personalCode &&
          check.type == type &&
          check.success == success
    })
  }

  def "does not add check if not missing"() {
    given:
    def user = sampleUserNonMember().build()
    def type = DOCUMENT
    def success = true
    def amlCheck = check(type, success, user)
    when:
    amlService.addCheckIfMissing(amlCheck)
    then:
    1 * amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(user.personalCode, type, aYearAgo) >> true
    0 * amlCheckRepository.save(_)
  }

  def "gives all checks"() {
    given:
    def user = sampleUserNonMember().build()
    when:
    amlService.getChecks(user)
    then:
    1 * amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(user.personalCode, aYearAgo)
  }

  def "does not do checks for second pillar"() {
    given:
    def user = sampleUser().build()
    def pillar = 2
    when:
    def result = amlService.allChecksPassed(user, pillar)
    then:
    result
  }

  def "sees if all checks are passed for third pillar"() {
    given:
    def user = sampleUser().build()
    def pillar = 3
    when:
    def actual = amlService.allChecksPassed(user, pillar)
    then:
    actual == result
    1 * amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(user.personalCode, aYearAgo) >> checks
    if (!result) {
      1 * eventPublisher.publishEvent(new TrackableEvent(user, TrackableEventType.MANDATE_DENIED))
    }
    where:
    checks                                                                                                      | result
    []                                                                                                          | false
    successfulChecks(POLITICALLY_EXPOSED_PERSON, RESIDENCY_AUTO, DOCUMENT, PENSION_REGISTRY_NAME, OCCUPATION)   | true
    successfulChecks(POLITICALLY_EXPOSED_PERSON, RESIDENCY_MANUAL, DOCUMENT, PENSION_REGISTRY_NAME, OCCUPATION) | true
    successfulChecks(POLITICALLY_EXPOSED_PERSON, RESIDENCY_AUTO, DOCUMENT, SK_NAME, OCCUPATION)                 | true
    successfulChecks(POLITICALLY_EXPOSED_PERSON, RESIDENCY_MANUAL, DOCUMENT, SK_NAME, OCCUPATION)               | true
    [check(POLITICALLY_EXPOSED_PERSON, false)] +
        successfulChecks(RESIDENCY_MANUAL, DOCUMENT, SK_NAME, PENSION_REGISTRY_NAME, OCCUPATION)                | false
  }

  def "checks for pep and sanctions"() {
    given:
    def user = sampleUser().build()
    def contactDetails = contactDetailsFixture()


    def properties = objectMapper.createObjectNode()
    properties.set("topics", objectMapper.createArrayNode().add("role.pep"))

    def result1 = objectMapper.createObjectNode()
    result1.set("properties", properties)
    result1.set("match", BooleanNode.TRUE)


    def results = objectMapper.createArrayNode().add(result1)
    def query = objectMapper.createObjectNode()
    def matchResponse = new MatchResponse(results, query)

    sanctionCheckService.match(user, "EE") >> matchResponse

    when:
    List<AmlCheck> checks = amlService.addSanctionAndPepCheckIfMissing(user, contactDetails)

    then:
    checks == [
        check(POLITICALLY_EXPOSED_PERSON_AUTO, false, user, [results: results, query: query]),
        check(SANCTION, true, user,  [results: results, query: query]),
    ]
  }

  private static List<AmlCheck> successfulChecks(AmlCheckType... checkTypes) {
    return checkTypes.collect({ type -> check(type) })
  }

  private static AmlCheck check(AmlCheckType type, boolean success = true, User user = null, Map<String, Object> metadata = [:]) {
    return AmlCheck.builder().type(type).success(success).personalCode(user?.personalCode).metadata(metadata).build()
  }
}
