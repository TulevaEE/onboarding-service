package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.user.member.Member
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

@DataJpaTest
class UserRepositorySpec extends Specification {

	@Autowired
	private TestEntityManager entityManager

	@Autowired
	private UserRepository repository

	def "persisting and findByPersonalCode() works"() {
		given:
		entityManager.persist(User.builder()
				.firstName("Erko")
				.lastName("Risthein")
				.personalCode("38501010002")
				.email("erko@risthein.ee")
				.phoneNumber("5555555")
				.active(true)
				.build())

		entityManager.flush()

		when:
		User user = repository.findByPersonalCode("38501010002").get()

		then:
		user.id != null
		user.firstName == "Erko"
		user.lastName == "Risthein"
		user.personalCode == "38501010002"
		user.email == "erko@risthein.ee"
		user.phoneNumber == "5555555"
		user.createdDate != null
		user.updatedDate != null
	}

	def "can not save null"() {
		when:
		repository.save(null)
		then:
		thrown RuntimeException
	}

    def "will not find inactive members"() {
        given:
        def sampleUser = entityManager.persist(User.builder()
            .personalCode("38501010002")
            .email("erko@risthein.ee")
            .firstName("Erko")
            .lastName("Risthein")
            .build())

        entityManager.persist(
            Member.builder()
                .user(sampleUser)
                .memberNumber(234)
                .active(false)
                .build()
        )

        entityManager.flush()

        when:
        User user = repository.findById(sampleUser.id).get()

        then:
        !user.getMember().isPresent()
    }

  def "can find a user by member id"() {
    given:
    def user = entityManager.persist(User.builder()
        .firstName("Erko")
        .lastName("Risthein")
        .personalCode("38501010002")
        .email("erko@risthein.ee")
        .phoneNumber("5555555")
        .active(true)
        .build())

    entityManager.flush()
    def member = entityManager.persist(Member.builder()
        .user(user)
        .memberNumber(234)
        .active(true)
        .build())
    entityManager.flush()

    when:
    def foundUser = repository.findByMember_Id(member.id).get()

    then:
    foundUser == user
  }

}
