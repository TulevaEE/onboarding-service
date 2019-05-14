package ee.tuleva.onboarding.aml


import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

@DataJpaTest
class AmlCheckRepositorySpec extends Specification {
    @Autowired
    private TestEntityManager entityManager

    @Autowired
    private AmlCheckRepository repository

    def "persisting and findById() works"() {
        given:
        User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())

        AmlCheck sampleCheck = AmlCheck.builder()
            .user(sampleUser)
            .type(AmlCheckType.DOCUMENT)
            .success(true)
            .build()

        entityManager.persist(sampleCheck)

        entityManager.flush()

        when:
        def check = repository.findById(sampleCheck.id)

        then:
        check.isPresent()
        check.get().id != null
        check.get().user == sampleUser
        check.get().type == AmlCheckType.DOCUMENT
    }
}
