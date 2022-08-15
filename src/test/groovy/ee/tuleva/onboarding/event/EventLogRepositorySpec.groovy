package ee.tuleva.onboarding.event

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import java.time.Instant

@DataJpaTest
class EventLogRepositorySpec extends Specification {

  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private EventLogRepository repository

  def "persisting and finding events works"() {
    given:
    def event = EventLog.builder()
        .type("LOGIN")
        .principal("38501010002")
        .timestamp(Instant.parse("2022-08-15T10:00:00Z"))
        .data([data: true])
        .build()
    entityManager.persist(event)
    entityManager.flush()

    when:
    def events = repository.findAll()

    then:
    events == [event]
  }

}
