package ee.tuleva.onboarding.user

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import java.time.Instant

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
				.createdDate(Instant.parse("2017-01-31T14:06:01Z"))
				.updatedDate(Instant.parse("2017-01-31T14:06:01Z"))
				.memberNumber(3000)
				.active(true)
				.build())

		entityManager.flush()

		when:
		User user = repository.findByPersonalCode("38501010002")

		then:
		user.id != null
		user.firstName == "Erko"
		user.lastName == "Risthein"
		user.personalCode == "38501010002"
		user.email == "erko@risthein.ee"
		user.phoneNumber == "5555555"
		user.createdDate == Instant.parse("2017-01-31T14:06:01Z")
		user.updatedDate == Instant.parse("2017-01-31T14:06:01Z")
		user.memberNumber == 3000
	}

}
