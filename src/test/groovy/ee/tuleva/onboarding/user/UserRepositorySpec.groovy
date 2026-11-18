package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.time.ClockHolder
import ee.tuleva.onboarding.user.member.Member
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest
class UserRepositorySpec extends Specification {

	@Autowired
	private TestEntityManager entityManager

	@Autowired
	private UserRepository repository

	@Autowired
	private JdbcClient jdbcClient

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

  def "locks a user by personal code for update"() {
    given:
    def user = entityManager.persistAndFlush(User.builder()
        .firstName("Test")
        .lastName("Person")
        .personalCode("38812121215")
        .email("old@example.com")
        .build())

    when:
    def foundUser = repository.lockForUpdate("38812121215")

    then:
    foundUser == Optional.of(user)
  }

  def "locks no user when the personal code is unknown"() {
    expect:
    repository.lockForUpdate("38812121215") == Optional.empty()
  }

  def "locking a user reads the row as committed rather than the copy already in the session"() {
    given:
    def user = entityManager.persistAndFlush(User.builder()
        .firstName("Test")
        .lastName("Person")
        .personalCode("38812121215")
        .email("old@example.com")
        .build())
    jdbcClient.sql("UPDATE users SET email = 'concurrent@example.com' WHERE id = :id")
        .param("id", user.id)
        .update()

    when:
    def lockedUser = repository.lockForUpdate("38812121215").get()

    then:
    lockedUser.email == "concurrent@example.com"
  }

  @Transactional(propagation = NOT_SUPPORTED)
  def "refuses to lock a user outside a transaction"() {
    when:
    repository.lockForUpdate("38812121215")

    then:
    thrown(IllegalTransactionStateException)
  }

  def "locking a member reads the user together with the membership"() {
    given:
    def user = entityManager.persist(User.builder()
        .firstName("Test")
        .lastName("Person")
        .personalCode("38812121215")
        .email("old@example.com")
        .build())
    def member = entityManager.persist(Member.builder()
        .user(user)
        .memberNumber(234)
        .active(true)
        .build())
    entityManager.flush()
    jdbcClient.sql("UPDATE users SET email = 'concurrent@example.com' WHERE id = :id")
        .param("id", user.id)
        .update()

    when:
    def lockedUser = repository.lockForUpdate("38812121215").get()

    then:
    lockedUser.email == "concurrent@example.com"
    lockedUser.getMember().map { it.id } == Optional.of(member.id)
    lockedUser.getMember().map { it.memberNumber } == Optional.of(234)
  }

  def "saving a change to another column leaves contact details another session committed in place"() {
    given:
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
    def user = entityManager.persistAndFlush(User.builder()
        .firstName("Test")
        .lastName("Person")
        .personalCode("38812121215")
        .email("old@example.com")
        .phoneNumber("+37255555555")
        .build())
    jdbcClient.sql("UPDATE users SET email = 'concurrent@example.com', phone_number = '+37251111111' WHERE id = :id")
        .param("id", user.id)
        .update()
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-11-18T11:00:00Z"), ZoneOffset.UTC))

    when:
    user.firstName = "Renamed"
    repository.save(user)
    entityManager.flush()

    then:
    jdbcClient.sql("SELECT first_name, email, phone_number, updated_date FROM users WHERE id = :id")
        .param("id", user.id)
        .query({ rs, rowNum ->
          [rs.getString("first_name"), rs.getString("email"), rs.getString("phone_number"),
           rs.getTimestamp("updated_date").toInstant()]
        })
        .single() == ["Renamed", "concurrent@example.com", "+37251111111", Instant.parse("2026-11-18T11:00:00Z")]

    cleanup:
    ClockHolder.setDefaultClock()
  }

}
