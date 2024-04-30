package ee.tuleva.onboarding.mandate.email.persistence

import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static EmailType.THIRD_PILLAR_SUGGEST_SECOND
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.*
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS

class EmailPersistenceServiceSpec extends Specification {

  EmailRepository emailRepository = Mock()
  EmailService emailService = Mock()
  Clock clock = TestClockHolder.clock

  EmailPersistenceService emailPersistenceService =
      new EmailPersistenceService(emailRepository, emailService, clock)

  def "creates scheduled email with correct attributes"() {
    given:
    Person person = samplePerson()
    Email email = new Email(
        personalCode: person.personalCode,
        mandrillMessageId: "12345",
        type: THIRD_PILLAR_SUGGEST_SECOND,
        status: SCHEDULED,
    )
    emailRepository.save(email) >> email

    when:
    Email savedEmail = emailPersistenceService.save(person, email.mandrillMessageId, email.type, email.status.toString())

    then:
    savedEmail == email
  }

  def "returns cancelled emails and deletes them from the database"() {
    given:
    Person person = samplePerson()
    EmailType type = THIRD_PILLAR_SUGGEST_SECOND
    List<Email> emails = [
        new Email(personalCode: person.personalCode, mandrillMessageId: "100", type: type),
        new Email(personalCode: person.personalCode, mandrillMessageId: "200", type: type)
    ]
    def scheduledMessageInfo = Optional.of(new MandrillScheduledMessageInfo())
    emailRepository.findAllByPersonalCodeAndTypeAndStatusOrderByCreatedDateDesc(person.personalCode, type, SCHEDULED) >> emails
    emailService.cancelScheduledEmail("100") >> scheduledMessageInfo
    emailService.cancelScheduledEmail("200") >> scheduledMessageInfo

    when:
    def cancelledEmails = emailPersistenceService.cancel(person, type)

    then:
    cancelledEmails == emails
    cancelledEmails.every { email -> email.status == CANCELLED }
    1 * emailRepository.saveAll(emails)
  }

  def "can check for todays emails"() {
    given:
    def person = samplePerson()
    def type = THIRD_PILLAR_SUGGEST_SECOND
    def email = new Email(
        personalCode: person.personalCode,
        mandrillMessageId: "100",
        type: type,
        status: SCHEDULED,
        createdDate: Instant.now(clock),
        updatedDate: Instant.now(clock)
    )
    def statuses = [SENT, QUEUED, SCHEDULED]
    emailRepository.
        findFirstByPersonalCodeAndTypeAndStatusInOrderByCreatedDateDesc(person.personalCode, type, statuses) >> Optional.of(email)

    when:
    def hasEmailsToday = emailPersistenceService.hasEmailsToday(person, type)

    then:
    hasEmailsToday
  }

  def "can save a scheduled email"() {
    given:
    def person = samplePerson()
    def email = new Email(
        personalCode: person.personalCode,
        mandrillMessageId: null,
        type: SECOND_PILLAR_LEAVERS,
        status: SCHEDULED,
    )
    emailRepository.save(email) >> email

    when:
    def savedEmail = emailPersistenceService.save(person, email.type, email.status)

    then:
    savedEmail == email
  }
}
