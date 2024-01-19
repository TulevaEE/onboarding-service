package ee.tuleva.onboarding.mandate.email.persistence

import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static EmailType.THIRD_PILLAR_SUGGEST_SECOND
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED

class EmailPersistenceServiceSpec extends Specification {

  EmailRepository emailRepository = Mock()
  EmailService emailService = Mock()

  EmailPersistenceService emailPersistenceService =
      new EmailPersistenceService(emailRepository, emailService)

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
    cancelledEmails.every { email -> email.status == EmailStatus.CANCELLED }
    1 * emailRepository.saveAll(emails)
  }
}
