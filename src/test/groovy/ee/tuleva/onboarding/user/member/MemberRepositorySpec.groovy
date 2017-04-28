package ee.tuleva.onboarding.user.member

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static java.time.Instant.now

@DataJpaTest
class MemberRepositorySpec extends Specification {

	@Autowired
	private TestEntityManager entityManager

	@Autowired
	private MemberRepository repository

	def "returns max member number"() {
		given:
		def persistedUser = entityManager.persist(sampleUserNonMember())
		def member = Member.builder()
				.user(persistedUser)
				.createdDate(now())
				.memberNumber(9999)
				.build()
		entityManager.persist(member)

		entityManager.flush()

		when:
		def maxMemberNumber = repository.getMaxMemberNumber()

		then:
		maxMemberNumber == 9999
	}

}
