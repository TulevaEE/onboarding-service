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

	def "returns max member number"() {
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
		def maxMemberNumber = repository.getMaxMemberNumber()

		then:
		maxMemberNumber == 9999
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
}
