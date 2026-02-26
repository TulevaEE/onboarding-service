package ee.tuleva.onboarding.aml

import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.StringNode
import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.aml.AmlCheckType.*
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static java.time.temporal.ChronoUnit.DAYS

@DataJpaTest
class AmlCheckRepositorySpec extends Specification {
  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private AmlCheckRepository repository

  JsonMapper objectMapper = JsonMapper.builder().build()

  def "persisting and findById() works"() {
    given:
    User sampleUser = sampleUser().build()

    def metadata = ["person": samplePerson()]
    AmlCheck sampleCheck = AmlCheck.builder()
        .personalCode(sampleUser.personalCode)
        .type(DOCUMENT)
        .success(true)
        .metadata(metadata)
        .build()

    entityManager.persist(sampleCheck)

    entityManager.flush()

    when:
    def check = repository.findById(sampleCheck.id)

    then:
    check.isPresent()
    check.get().id != null
    check.get().personalCode == sampleUser.personalCode
    check.get().type == DOCUMENT
    check.get().metadata == metadata
  }

  def "exists by user and type and created after works with past date"() {
    given:
    User sampleUser = sampleUser().build()

    AmlCheck sampleCheck = AmlCheck.builder()
        .personalCode(sampleUser.personalCode)
        .type(DOCUMENT)
        .success(true)
        .build()

    entityManager.persist(sampleCheck)

    entityManager.flush()

    Instant createdAfter = Instant.now().minus(365, DAYS)

    when:
    def exists = repository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(sampleUser.personalCode, DOCUMENT, createdAfter)

    then:
    exists
  }

  def "exists by user and type and created after works with future date"() {
    given:
    User sampleUser = sampleUser().build()

    AmlCheck sampleCheck = AmlCheck.builder()
        .personalCode(sampleUser.personalCode)
        .type(DOCUMENT)
        .success(true)
        .build()

    entityManager.persist(sampleCheck)

    entityManager.flush()

    Instant createdAfter = Instant.now().plus(365, DAYS)

    when:
    def exists = repository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(sampleUser.personalCode, DOCUMENT, createdAfter)

    then:
    !exists
  }

  def "findAllByPersonalCodeAndCreatedTimeAfter() works with past date"() {
    given:
    User sampleUser = sampleUser().build()

    AmlCheck sampleCheck = AmlCheck.builder()
        .personalCode(sampleUser.personalCode)
        .type(DOCUMENT)
        .success(true)
        .build()

    entityManager.persist(sampleCheck)

    entityManager.flush()

    Instant createdAfter = Instant.now().minus(365, DAYS)

    when:
    def checks = repository.findAllByPersonalCodeAndCreatedTimeAfter(sampleUser.personalCode, createdAfter)

    then:
    checks.first().id != null
    checks.first().personalCode == sampleUser.personalCode
    checks.first().type == DOCUMENT
    checks.size() == 1
  }

  def "findAllByUserAndCreatedTimeAfter() works with future date"() {
    given:
    User sampleUser = sampleUser().build()

    AmlCheck sampleCheck = AmlCheck.builder()
        .personalCode(sampleUser.personalCode)
        .type(DOCUMENT)
        .success(true)
        .build()

    entityManager.persist(sampleCheck)

    entityManager.flush()

    Instant createdAfter = Instant.now().plus(365, DAYS)

    when:
    def checks = repository.findAllByPersonalCodeAndCreatedTimeAfter(sampleUser.personalCode, createdAfter)

    then:
    checks == []
  }

  def "can save JsonNode as metadata"() {
    given:
    User sampleUser = sampleUser().build()

    def results = objectMapper.createArrayNode()
    results.add(new StringNode("result1"))
    def metadata = ["results": results]

    AmlCheck sampleCheck = AmlCheck.builder()
        .personalCode(sampleUser.personalCode)
        .type(SANCTION)
        .success(false)
        .metadata(metadata)
        .build()

    entityManager.persist(sampleCheck)

    entityManager.flush()

    when:
    def check = repository.findById(sampleCheck.id)

    then:
    check.isPresent()
    check.get().id != null
    check.get().personalCode == sampleUser.personalCode
    check.get().type == SANCTION
    check.get().metadata == metadata
  }

  def "can find match overrides"() {
    given:
    User sampleUser = sampleUser().build()

    def results = objectMapper.createArrayNode()
    def result = objectMapper.createObjectNode()
    result.put("id", "123")
    results.add(result)
    def metadata = ["results": results]

    AmlCheck sampleOverride = AmlCheck.builder()
        .personalCode(sampleUser.personalCode)
        .type(SANCTION_OVERRIDE)
        .success(true)
        .metadata(metadata)
        .build()

    entityManager.persist(sampleOverride)

    entityManager.flush()

    when:
    def checks = repository.findAllByPersonalCodeAndTypeAndSuccess(sampleUser.personalCode, SANCTION_OVERRIDE, true)

    then:
    checks == [sampleOverride]
  }
}
