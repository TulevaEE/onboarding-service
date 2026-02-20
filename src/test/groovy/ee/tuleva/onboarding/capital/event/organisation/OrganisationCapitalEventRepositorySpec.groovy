package ee.tuleva.onboarding.capital.event.organisation

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

@DataJpaTest
public class OrganisationCapitalEventRepositorySpec extends Specification {

    @Autowired
    private TestEntityManager entityManager

    @Autowired
    private OrganisationCapitalEventRepository repository

    def "persiting and finding works"() {
        given:
        OrganisationCapitalEvent event = OrganisationCapitalEventFixture.fixture().build()
        OrganisationCapitalEvent persistedEvent = entityManager.persist(event)

        entityManager.flush()
        when:

        List<OrganisationCapitalEvent> events = repository.findAll()
        OrganisationCapitalEvent foundEvent = events.first()

        then:
        events.size() == 1
        foundEvent.id == persistedEvent.id
        foundEvent.type == persistedEvent.type
        foundEvent.fiatValue == persistedEvent.fiatValue
    }

    def "Gets total value"() {
        given:
        OrganisationCapitalEvent event = OrganisationCapitalEventFixture.fixture().build()
        entityManager.persist(event)
        OrganisationCapitalEvent event2 = OrganisationCapitalEventFixture.fixture().build()
        entityManager.persist(event2)

        entityManager.flush()
        when:

        BigDecimal totalValue = repository.getTotalValue()

        then:
        Math.round(totalValue) == Math.round(event.fiatValue + event2.fiatValue)
    }
}
