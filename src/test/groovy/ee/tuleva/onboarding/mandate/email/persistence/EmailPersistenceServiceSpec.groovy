package ee.tuleva.onboarding.mandate.email.persistence

import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.mandate.batch.MandateBatchFixture
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static EmailType.THIRD_PILLAR_SUGGEST_SECOND
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.*
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_EARLY_WITHDRAWAL
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.WITHDRAWAL_BATCH

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
    emailRepository.findAllByPersonalCodeAndTypeAndStatusInOrderByCreatedDateDesc(person.personalCode, type, [SCHEDULED, QUEUED]) >> emails
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
    def mandate = sampleMandate()
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
    emailRepository.findFirstByPersonalCodeAndTypeAndMandateAndStatusInOrderByCreatedDateDesc(
        person.personalCode, type, mandate, statuses) >> Optional.of(email)

    when:
    def hasEmailsToday = emailPersistenceService.hasEmailsToday(person, type, mandate)

    then:
    hasEmailsToday
  }

  def "can check for todays emails for mandate that is part of a batch "() {
    given:
    def person = samplePerson()

    def mandate1 = sampleFundPensionOpeningMandate()
    def mandate2 = samplePartialWithdrawalMandate()
    def mandateBatch =
        MandateBatchFixture.aMandateBatch().mandates(List.of(mandate1, mandate2)).build()

    mandate1.setMandateBatch(mandateBatch)
    mandate2.setMandateBatch(mandateBatch)

    def type = WITHDRAWAL_BATCH
    def email = new Email(
        personalCode: person.personalCode,
        mandrillMessageId: "100",
        type: type,
        status: SCHEDULED,
        createdDate: Instant.now(clock),
        updatedDate: Instant.now(clock),
        mandateBatch: mandateBatch,
    )
    def statuses = [SENT, QUEUED, SCHEDULED]
    emailRepository.findFirstByPersonalCodeAndTypeAndMandateBatchAndStatusInOrderByCreatedDateDesc(
        person.personalCode, type, mandateBatch, statuses) >> Optional.of(email)

    when:
    def hasEmailsToday = emailPersistenceService.hasEmailsToday(person, type, mandate1)

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

  def "can save a scheduled email for a mandate batch"() {
    given:
    def person = samplePerson()
    def mandate1 = sampleFundPensionOpeningMandate()
    def mandate2 = samplePartialWithdrawalMandate()

    def mandateBatch =
        MandateBatchFixture.aMandateBatch().mandates(List.of(mandate1, mandate2)).build()

    def email = new Email(
        personalCode: person.personalCode,
        mandrillMessageId: null,
        type: SECOND_PILLAR_LEAVERS,
        status: SCHEDULED,
        mandateBatch: mandateBatch,
    )
    emailRepository.save(email) >> email

    when:
    def savedEmail = emailPersistenceService.save(person, null, email.type, email.status.name(), mandateBatch)

    then:
    savedEmail == email
  }

  def "can find last email sent date"() {
    given:
    def person = samplePerson()
    def type = SECOND_PILLAR_EARLY_WITHDRAWAL
    def date = Instant.now(clock)
    def email = new Email(
        personalCode: person.personalCode,
        type: type,
        status: SCHEDULED,
        createdDate: date,
        updatedDate: date
    )
    emailRepository.findFirstByPersonalCodeAndTypeOrderByCreatedDateDesc(person.personalCode, type) >> Optional.of(email)

    when:
    def lastEmailDate = emailPersistenceService.getLastEmailSendDate(person, type)

    then:
    lastEmailDate.get() == date
  }

  def "can check if email type has been sent before"() {
    given:
    def type = SECOND_PILLAR_LEAVERS

    when:
    emailRepository.existsByType(type) >> true
    def hasBeenSent = emailPersistenceService.hasEmailTypeBeenSentBefore(type)

    then:
    hasBeenSent
  }

  def "returns false when email type has not been sent before"() {
    given:
    def type = SECOND_PILLAR_LEAVERS

    when:
    emailRepository.existsByType(type) >> false
    def hasBeenSent = emailPersistenceService.hasEmailTypeBeenSentBefore(type)

    then:
    !hasBeenSent
  }
}
