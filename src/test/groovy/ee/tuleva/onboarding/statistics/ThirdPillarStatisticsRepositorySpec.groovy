package ee.tuleva.onboarding.statistics

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.simpleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.emptyMandate
import static ee.tuleva.onboarding.statistics.ThirdPillarStatisticsFixture.sampleThirdPillarStatistics

@DataJpaTest
class ThirdPillarStatisticsRepositorySpec extends Specification {

    @Autowired
    private TestEntityManager entityManager

    @Autowired
    private ThirdPillarStatisticsRepository repository

    def "can persist and get third pillar statistics"() {
        given:
        def user = simpleUser().build()
        entityManager.persistAndFlush(user)

        def mandate = emptyMandate().user(user).build()
        entityManager.persistAndFlush(mandate)

        def statistics = sampleThirdPillarStatistics().mandateId(mandate.id).build()
        entityManager.persistAndFlush(statistics)

        when:
        def allStatistics = repository.findAll()

        then:
        allStatistics == [statistics]
    }

}
