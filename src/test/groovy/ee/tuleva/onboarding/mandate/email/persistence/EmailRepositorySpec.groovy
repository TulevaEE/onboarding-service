package ee.tuleva.onboarding.mandate.email.persistence

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED

@DataJpaTest
class EmailRepositorySpec extends Specification {

  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private EmailRepository emailRepository

  def "persisting and finding works"() {
    given:
    def user = entityManager.persist(sampleUserNonMember().id(null).build())
    def emailType = EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
    def scheduledEmail = entityManager.persist(
        new Email(userId: user.id, mandrillMessageId: "mandrillMessageId123", type: emailType, status: SCHEDULED)
    )
    entityManager.flush()

    when:
    def scheduledEmails =
        emailRepository.findAllByUserIdAndTypeAndStatusOrderByCreatedDateDesc(user.id, emailType, SCHEDULED)

    then:
    scheduledEmails == [scheduledEmail]
  }

  def "can find latest email"() {
    given:
    def user = entityManager.persist(sampleUserNonMember().id(null).build())
    def emailType = EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
    def scheduledEmail = entityManager.persist(
        new Email(userId: user.id, mandrillMessageId: "mandrillMessageId123", type: emailType, status: SCHEDULED)
    )
    entityManager.flush()
    def statuses = new EmailStatus[]{SCHEDULED}

    when:
    Optional<Email> latestEmail = emailRepository.findLatestEmail(user.id, emailType, statuses)

    then:
    latestEmail.get() == scheduledEmail
  }
}
