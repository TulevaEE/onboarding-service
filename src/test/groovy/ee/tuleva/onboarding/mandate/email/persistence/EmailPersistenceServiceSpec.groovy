package ee.tuleva.onboarding.mandate.email.persistence

import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.time.TestClockHolder
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static EmailType.THIRD_PILLAR_SUGGEST_SECOND
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.*

class EmailPersistenceServiceSpec extends Specification {

  EmailRepository emailRepository = Mock()
  EmailService emailService = Mock()
  Clock clock = TestClockHolder.clock

  EmailPersistenceService emailPersistenceService =
      new EmailPersistenceService(emailRepository, emailService, clock)

  def "creates scheduled email with correct attributes"() {
    given:
    User user = new User(id: 13);
    String messageId = "12345"
    EmailType type = THIRD_PILLAR_SUGGEST_SECOND
    String status = "scheduled"

    when:
    emailPersistenceService.save(user, messageId, type, status)

    then:
    1 * emailRepository.save({
      Email email ->
        email.userId == user.getId() &&
            email.type == THIRD_PILLAR_SUGGEST_SECOND &&
            email.mandrillMessageId == messageId &&
            email.status == SCHEDULED
    })
  }

  def "returns cancelled emails and deletes them from the database"() {
    given:
    User user = new User(id: 13)
    EmailType type = THIRD_PILLAR_SUGGEST_SECOND
    List<Email> emails = [
        new Email(userId: user.id, mandrillMessageId: "100", type: type),
        new Email(userId: user.id, mandrillMessageId: "200", type: type)
    ]
    def scheduledMessageInfo = Optional.of(new MandrillScheduledMessageInfo())
    emailRepository.findAllByUserIdAndTypeAndStatusOrderByCreatedDateDesc(user.id, type, SCHEDULED) >> emails
    emailService.cancelScheduledEmail("100") >> scheduledMessageInfo
    emailService.cancelScheduledEmail("200") >> scheduledMessageInfo

    when:
    def cancelledEmails = emailPersistenceService.cancel(user, type)

    then:
    cancelledEmails == emails
    cancelledEmails.every { email -> email.status == CANCELLED }
    1 * emailRepository.saveAll(emails)
  }

  def "can check for todays emails"() {
    given:
    def user = new User(id: 13)
    def type = THIRD_PILLAR_SUGGEST_SECOND
    def email = new Email(
        userId: user.id,
        mandrillMessageId: "100",
        type: type,
        status: SCHEDULED,
        createdDate: Instant.now(clock),
        updatedDate: Instant.now(clock)
    )
    def statuses = [SENT, QUEUED, SCHEDULED]
    emailRepository.
        findFirstByUserIdAndTypeAndStatusInOrderByCreatedDateDesc(user.id, type, statuses) >> Optional.of(email)

    when:
    def hasEmailsToday = emailPersistenceService.hasEmailsToday(user, type)

    then:
    hasEmailsToday
  }
}
