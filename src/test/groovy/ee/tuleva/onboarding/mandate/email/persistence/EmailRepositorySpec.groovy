package ee.tuleva.onboarding.mandate.email.persistence

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.mandate.MandateFixture.emptyMandate
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE

@DataJpaTest
class EmailRepositorySpec extends Specification {

  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private EmailRepository emailRepository

  def "persisting and finding works"() {
    given:
    def person = samplePerson()
    def emailType = THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
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

  def "can find latest email without mandate"() {
    given:
    def person = samplePerson()
    def mandate = null
    def emailType = THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
    def scheduledEmail1 = entityManager.persist(
        new Email(personalCode: person.personalCode, mandrillMessageId: "123", type: emailType, status: SCHEDULED)
    )
    def scheduledEmail2 = entityManager.persist(
        new Email(personalCode: person.personalCode, mandrillMessageId: "234", type: emailType, status: SCHEDULED)
    )
    entityManager.flush()
    def statuses = [SCHEDULED]

    when:
    Optional<Email> latestEmail = emailRepository.findFirstByPersonalCodeAndTypeAndMandateAndStatusInOrderByCreatedDateDesc(
        person.personalCode, emailType, mandate, statuses)

    then:
    latestEmail.get() == scheduledEmail2
  }


  def "can find latest email with mandate"() {
    given:
    def emailType = THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
    def sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())
    def sampleMandate = entityManager.persist(emptyMandate().user(sampleUser).build())
    def scheduledEmail1 = entityManager.persist(
        new Email(personalCode: sampleUser.personalCode, mandrillMessageId: "123", type: emailType, mandate: sampleMandate, status: SCHEDULED)
    )
    def scheduledEmail2 = entityManager.persist(
        new Email(personalCode: sampleUser.personalCode, mandrillMessageId: "234", type: emailType, mandate: sampleMandate, status: SCHEDULED)
    )
    entityManager.flush()
    def statuses = [SCHEDULED]

    when:
    Optional<Email> latestEmail = emailRepository.findFirstByPersonalCodeAndTypeAndMandateAndStatusInOrderByCreatedDateDesc(
        sampleUser.personalCode, emailType, sampleMandate, statuses)

    then:
    latestEmail.get() == scheduledEmail2
  }
}
