package ee.tuleva.onboarding.notification.email

import ee.tuleva.onboarding.notification.email.persistence.EmailRepository
import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.mandate.batch.MandateBatchFixture
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.util.Locale

import static EmailType.THIRD_PILLAR_SUGGEST_SECOND
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate
import static ee.tuleva.onboarding.notification.email.EmailStatus.*
import static ee.tuleva.onboarding.notification.email.EmailType.SECOND_PILLAR_EARLY_WITHDRAWAL
import static ee.tuleva.onboarding.notification.email.EmailType.SECOND_PILLAR_LEAVERS
import static ee.tuleva.onboarding.notification.email.EmailType.WITHDRAWAL_BATCH

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
    emailRepository.findAllByPersonalCodeAndTypeAndStatusInOrderByCreatedDateDescIdDesc(person.personalCode, type, [SCHEDULED, QUEUED]) >> emails
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
    emailRepository.findFirstByPersonalCodeAndTypeAndMandateIdAndStatusInOrderByCreatedDateDescIdDesc(
        person.personalCode, type, mandate.id, statuses) >> Optional.of(email)

    when:
    def hasEmailsToday = emailPersistenceService.hasMandateEmailsToday(person, type, mandate.id)

    then:
    hasEmailsToday
  }

  def "returns false for todays mandate emails when none exist"() {
    given:
    def person = samplePerson()
    def mandate = sampleMandate()
    def type = THIRD_PILLAR_SUGGEST_SECOND
    def statuses = [SENT, QUEUED, SCHEDULED]
    emailRepository.findFirstByPersonalCodeAndTypeAndMandateIdAndStatusInOrderByCreatedDateDescIdDesc(
        person.personalCode, type, mandate.id, statuses) >> Optional.empty()

    when:
    def hasEmailsToday = emailPersistenceService.hasMandateEmailsToday(person, type, mandate.id)

    then:
    !hasEmailsToday
  }

  def "returns false for todays mandate emails when the most recent one is not from today"() {
    given:
    def person = samplePerson()
    def mandate = sampleMandate()
    def type = THIRD_PILLAR_SUGGEST_SECOND
    def yesterday = Instant.now(clock).minus(1, java.time.temporal.ChronoUnit.DAYS)
    def email = new Email(
        personalCode: person.personalCode,
        mandrillMessageId: "100",
        type: type,
        status: SCHEDULED,
        createdDate: yesterday,
        updatedDate: yesterday
    )
    def statuses = [SENT, QUEUED, SCHEDULED]
    emailRepository.findFirstByPersonalCodeAndTypeAndMandateIdAndStatusInOrderByCreatedDateDescIdDesc(
        person.personalCode, type, mandate.id, statuses) >> Optional.of(email)

    when:
    def hasEmailsToday = emailPersistenceService.hasMandateEmailsToday(person, type, mandate.id)

    then:
    !hasEmailsToday
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
        mandateBatchId: mandateBatch.id,
    )
    def statuses = [SENT, QUEUED, SCHEDULED]
    emailRepository.findFirstByPersonalCodeAndTypeAndMandateBatchIdAndStatusInOrderByCreatedDateDescIdDesc(
        person.personalCode, type, mandateBatch.id, statuses) >> Optional.of(email)

    when:
    def hasEmailsToday = emailPersistenceService.hasMandateBatchEmailsToday(person, type, mandateBatch.id)

    then:
    hasEmailsToday
  }

  def "returns false for todays mandate batch emails when none exist"() {
    given:
    def person = samplePerson()
    def mandateBatch = MandateBatchFixture.aMandateBatch().build()
    def type = WITHDRAWAL_BATCH
    def statuses = [SENT, QUEUED, SCHEDULED]
    emailRepository.findFirstByPersonalCodeAndTypeAndMandateBatchIdAndStatusInOrderByCreatedDateDescIdDesc(
        person.personalCode, type, mandateBatch.id, statuses) >> Optional.empty()

    when:
    def hasEmailsToday = emailPersistenceService.hasMandateBatchEmailsToday(person, type, mandateBatch.id)

    then:
    !hasEmailsToday
  }

  def "returns false for todays mandate batch emails when the most recent one is not from today"() {
    given:
    def person = samplePerson()
    def mandateBatch = MandateBatchFixture.aMandateBatch().build()
    def type = WITHDRAWAL_BATCH
    def yesterday = Instant.now(clock).minus(1, java.time.temporal.ChronoUnit.DAYS)
    def email = new Email(
        personalCode: person.personalCode,
        mandrillMessageId: "100",
        type: type,
        status: SCHEDULED,
        createdDate: yesterday,
        updatedDate: yesterday,
        mandateBatchId: mandateBatch.id,
    )
    def statuses = [SENT, QUEUED, SCHEDULED]
    emailRepository.findFirstByPersonalCodeAndTypeAndMandateBatchIdAndStatusInOrderByCreatedDateDescIdDesc(
        person.personalCode, type, mandateBatch.id, statuses) >> Optional.of(email)

    when:
    def hasEmailsToday = emailPersistenceService.hasMandateBatchEmailsToday(person, type, mandateBatch.id)

    then:
    !hasEmailsToday
  }

  def "reports whether emails exist for a mandate"() {
    given:
    def person = samplePerson()
    def email = new Email(personalCode: person.personalCode, mandrillMessageId: "100", type: THIRD_PILLAR_SUGGEST_SECOND)
    emailRepository.findAllByMandateId(42L) >> [email]

    expect:
    emailPersistenceService.hasEmailsForMandate(42L)
  }

  def "reports that no emails exist for a mandate"() {
    given:
    emailRepository.findAllByMandateId(42L) >> []

    expect:
    !emailPersistenceService.hasEmailsForMandate(42L)
  }

  def "reports whether emails exist for a mandate batch"() {
    given:
    def person = samplePerson()
    def email = new Email(personalCode: person.personalCode, mandrillMessageId: "100", type: WITHDRAWAL_BATCH)
    emailRepository.findAllByMandateBatchId(42L) >> [email]

    expect:
    emailPersistenceService.hasEmailsForMandateBatch(42L)
  }

  def "reports that no emails exist for a mandate batch"() {
    given:
    emailRepository.findAllByMandateBatchId(42L) >> []

    expect:
    !emailPersistenceService.hasEmailsForMandateBatch(42L)
  }

  def "can save a scheduled email for a mandate"() {
    given:
    def person = samplePerson()
    def email = new Email(
        personalCode: person.personalCode,
        mandrillMessageId: "100",
        type: THIRD_PILLAR_SUGGEST_SECOND,
        status: SCHEDULED,
        mandateId: 42L,
    )
    emailRepository.save(email) >> email

    when:
    def savedEmail = emailPersistenceService.saveWithMandate(person, "100", THIRD_PILLAR_SUGGEST_SECOND, SCHEDULED.name(), 42L)

    then:
    savedEmail == email
  }

  def "can find an email by its mandrill message id"() {
    given:
    def person = samplePerson()
    def email = new Email(
        personalCode: person.personalCode,
        mandrillMessageId: "100",
        type: THIRD_PILLAR_SUGGEST_SECOND,
        status: SCHEDULED,
    )
    emailRepository.findByMandrillMessageId("100") >> Optional.of(email)

    expect:
    emailPersistenceService.findByMandrillMessageId("100") == Optional.of(email)
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
        mandateBatchId: mandateBatch.id,
    )
    emailRepository.save(email) >> email

    when:
    def savedEmail = emailPersistenceService.saveWithMandateBatch(person, null, email.type, email.status.name(), mandateBatch.id)

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
    emailRepository.findFirstByPersonalCodeAndTypeOrderByCreatedDateDescIdDesc(person.personalCode, type) >> Optional.of(email)

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

  def "combines the template name with a locale's language"() {
    expect:
    THIRD_PILLAR_SUGGEST_SECOND.getTemplateName(Locale.ENGLISH) == "third_pillar_suggest_second_en"
  }
}
