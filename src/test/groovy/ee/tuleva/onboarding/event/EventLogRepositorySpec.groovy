package ee.tuleva.onboarding.event

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

import java.time.Instant

@DataJpaTest
class EventLogRepositorySpec extends Specification {

  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private EventLogRepository repository

  def setup() {
    repository.deleteAll()
  }

  def "persisting and finding events works"() {
    given:
    repository.deleteAll()
    def event = EventLog.builder()
        .type("LOGIN")
        .principal("38501010002")
        .timestamp(Instant.parse("2022-08-15T10:00:00Z"))
        .data([data: [subdata: true]])
        .build()
    entityManager.persist(event)
    entityManager.flush()

    when:
    def events = repository.findAll()

    then:
    events == [event]
  }

  def "existsByTypeAndPrincipal is true for a recorded type and principal"() {
    given:
    repository.save(EventLog.builder()
        .type("DEFERRAL")
        .principal("mandrill_msg_1")
        .timestamp(Instant.parse("2026-06-15T15:20:00Z"))
        .data([recipient: "trustee@seb.ee"])
        .build())

    expect:
    repository.existsByTypeAndPrincipal("DEFERRAL", "mandrill_msg_1")
  }

  def "existsByTypeAndPrincipal is false for a different type or principal"() {
    given:
    repository.save(EventLog.builder()
        .type("DEFERRAL")
        .principal("mandrill_msg_1")
        .timestamp(Instant.parse("2026-06-15T15:20:00Z"))
        .data([recipient: "trustee@seb.ee"])
        .build())

    expect:
    !repository.existsByTypeAndPrincipal("HARD_BOUNCE", "mandrill_msg_1")
    !repository.existsByTypeAndPrincipal("DEFERRAL", "mandrill_msg_2")
  }

}
