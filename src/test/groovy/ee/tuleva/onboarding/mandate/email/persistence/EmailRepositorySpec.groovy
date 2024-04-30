package ee.tuleva.onboarding.mandate.email.persistence

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED

@DataJpaTest
class EmailRepositorySpec extends Specification {

  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private EmailRepository emailRepository

  def "persisting and finding works"() {
    given:
    def person = samplePerson()
    def emailType = EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
    def scheduledEmail = entityManager.persist(
        new Email(personalCode: person.personalCode, mandrillMessageId: "123", type: emailType, status: SCHEDULED)
    )
    entityManager.flush()

    when:
    def scheduledEmails =
        emailRepository.findAllByPersonalCodeAndTypeAndStatusOrderByCreatedDateDesc(person.personalCode, emailType, SCHEDULED)

    then:
    scheduledEmails == [scheduledEmail]
  }

  def "can find latest email"() {
    given:
    def person = samplePerson()
    def emailType = EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
    def scheduledEmail1 = entityManager.persist(
        new Email(personalCode: person.personalCode, mandrillMessageId: "123", type: emailType, status: SCHEDULED)
    )
    def scheduledEmail2 = entityManager.persist(
        new Email(personalCode: person.personalCode, mandrillMessageId: "234", type: emailType, status: SCHEDULED)
    )
    entityManager.flush()
    def statuses = [SCHEDULED]

    when:
    Optional<Email> latestEmail =
        emailRepository.findFirstByPersonalCodeAndTypeAndStatusInOrderByCreatedDateDesc(person.personalCode, emailType, statuses)

    then:
    latestEmail.get() == scheduledEmail2
  }
}
