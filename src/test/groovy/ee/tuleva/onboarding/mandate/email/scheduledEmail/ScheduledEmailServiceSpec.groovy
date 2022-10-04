package ee.tuleva.onboarding.mandate.email.scheduledEmail

import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType.SUGGEST_SECOND_PILLAR

class ScheduledEmailServiceSpec extends Specification {

  ScheduledEmailRepository scheduledEmailRepository = Mock()
  EmailService emailService = Mock()

  ScheduledEmailService scheduledEmailService =
      new ScheduledEmailService(scheduledEmailRepository, emailService)

  def "creates scheduled email with correct attributes"() {
    given:
    User user = new User(id: 13);
    String messageId = "12345"
    ScheduledEmailType type = SUGGEST_SECOND_PILLAR

    when:
    scheduledEmailService.create(user, messageId, type)

    then:
    1 * scheduledEmailRepository.save({
      ScheduledEmail email ->
        email.userId == user.getId() &&
            email.type == SUGGEST_SECOND_PILLAR &&
            email.mandrillMessageId == messageId
    })
  }

  def "returns cancelled emails and deletes them from the database"() {
    given:
    User user = new User(id: 13)
    ScheduledEmailType type = SUGGEST_SECOND_PILLAR
    List<ScheduledEmail> emails = [
        new ScheduledEmail(userId: user.id, mandrillMessageId: "100", type: type),
        new ScheduledEmail(userId: user.id, mandrillMessageId: "200", type: type)
    ]
    def scheduledMessageInfo = Optional.of(new MandrillScheduledMessageInfo())
    scheduledEmailRepository.findAllByUserIdAndTypeOrderByCreatedDateDesc(user.id, type) >> emails
    emailService.cancelScheduledEmail("100") >> scheduledMessageInfo
    emailService.cancelScheduledEmail("200") >> scheduledMessageInfo

    when:
    def cancelledEmails = scheduledEmailService.cancel(user, type)

    then:
    cancelledEmails == emails
    1 * scheduledEmailRepository.deleteAll(emails)
  }
}
