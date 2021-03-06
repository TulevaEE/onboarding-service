package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.aml.AmlCheckType.DOCUMENT
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static java.time.temporal.ChronoUnit.DAYS

@DataJpaTest
class AmlCheckRepositorySpec extends Specification {
    @Autowired
    private TestEntityManager entityManager

    @Autowired
    private AmlCheckRepository repository

    def "persisting and findById() works"() {
        given:
        User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())

        def metadata = ["occupation": "asdfg"]
        AmlCheck sampleCheck = AmlCheck.builder()
            .user(sampleUser)
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
        check.get().user == sampleUser
        check.get().type == DOCUMENT
        check.get().metadata == metadata
    }

    def "exists by user and type and created after works with past date"() {
        given:
        User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())

        AmlCheck sampleCheck = AmlCheck.builder()
            .user(sampleUser)
            .type(DOCUMENT)
            .success(true)
            .build()

        entityManager.persist(sampleCheck)

        entityManager.flush()

        Instant createdAfter = Instant.now().minus(365, DAYS)

        when:
        def exists = repository.existsByUserAndTypeAndCreatedTimeAfter(sampleUser, DOCUMENT, createdAfter)

        then:
        exists
    }

    def "exists by user and type and created after works with future date"() {
        given:
        User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())

        AmlCheck sampleCheck = AmlCheck.builder()
            .user(sampleUser)
            .type(DOCUMENT)
            .success(true)
            .build()

        entityManager.persist(sampleCheck)

        entityManager.flush()

        Instant createdAfter = Instant.now().plus(365, DAYS)

        when:
        def exists = repository.existsByUserAndTypeAndCreatedTimeAfter(sampleUser, DOCUMENT, createdAfter)

        then:
        !exists
    }

    def "findAllByUserAndCreatedTimeAfter() works with past date"() {
        given:
        User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())

        AmlCheck sampleCheck = AmlCheck.builder()
            .user(sampleUser)
            .type(DOCUMENT)
            .success(true)
            .build()

        entityManager.persist(sampleCheck)

        entityManager.flush()

        Instant createdAfter = Instant.now().minus(365, DAYS)

        when:
        def checks = repository.findAllByUserAndCreatedTimeAfter(sampleUser, createdAfter)

        then:
        checks.first().id != null
        checks.first().user == sampleUser
        checks.first().type == DOCUMENT
        checks.size() == 1
    }

    def "findAllByUserAndCreatedTimeAfter() works with future date"() {
        given:
        User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())

        AmlCheck sampleCheck = AmlCheck.builder()
            .user(sampleUser)
            .type(DOCUMENT)
            .success(true)
            .build()

        entityManager.persist(sampleCheck)

        entityManager.flush()

        Instant createdAfter = Instant.now().plus(365, DAYS)

        when:
        def checks = repository.findAllByUserAndCreatedTimeAfter(sampleUser, createdAfter)

        then:
        checks == []
    }
}
