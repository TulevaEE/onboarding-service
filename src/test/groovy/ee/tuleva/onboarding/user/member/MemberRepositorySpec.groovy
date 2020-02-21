package ee.tuleva.onboarding.user.member

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

@DataJpaTest
class MemberRepositorySpec extends Specification {

    @Autowired
    private TestEntityManager entityManager

    @Autowired
    private MemberRepository repository

    def "returns next member number"() {
        given:
        def nonPersistedUser = sampleUserNonMember().id(null).build()
        def persistedUser = entityManager.persist(nonPersistedUser)
        def member = Member.builder()
            .user(persistedUser)
            .memberNumber(9999)
            .build()
        entityManager.persist(member)

        entityManager.flush()

        when:
        def maxMemberNumber = repository.getNextMemberNumber()

        then:
        maxMemberNumber == 10000
    }

    def "persisting a new member generates the created date field"() {
        given:
        def nonPersistedUser = sampleUserNonMember().id(null).build()
        def persistedUser = entityManager.persist(nonPersistedUser)
        def member = Member.builder()
            .user(persistedUser)
            .memberNumber(1234)
            .build()

        when:
        def persistedMember = repository.save(member)

        then:
        persistedMember.createdDate != null
    }

    def "only returns active members"() {
        given:
        def user1 = entityManager.persist(
            sampleUserNonMember()
                .id(null)
                .email("a@b.ee")
                .personalCode("38812121215")
                .build()
        )
        def user2 = entityManager.persist(
            sampleUserNonMember()
                .id(null)
                .email("c@d.ee")
                .personalCode("38512121217")
                .build()
        )
        entityManager.persist(
            Member.builder()
                .user(user1)
                .memberNumber(123)
                .active(true)
                .build()
        )
        entityManager.persist(
            Member.builder()
                .user(user2)
                .memberNumber(234)
                .active(false)
                .build()
        )
        entityManager.flush()

        when:
        def members = repository.findAll()

        then:
        members.every { it.active }
    }
}
