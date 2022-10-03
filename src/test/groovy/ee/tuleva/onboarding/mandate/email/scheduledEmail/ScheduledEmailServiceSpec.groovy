package ee.tuleva.onboarding.mandate.email.scheduledEmail

import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType.SUGGEST_SECOND_PILLAR

class ScheduledEmailServiceSpec extends Specification {

  ScheduledEmailRepository scheduledEmailRepository = Mock()
  EmailService emailService = Mock()

  ScheduledEmailService service = new ScheduledEmailService(scheduledEmailRepository, emailService)

  def 'creates scheduled email with correct attributes'() {
    given:
    User user = new User(id: 13);
    String messageId = "12345"
    ScheduledEmailType type = SUGGEST_SECOND_PILLAR

    when:
    service.create(user, messageId, type)

    then:
    1 * scheduledEmailRepository.save({
      ScheduledEmail email ->
        email.userId == user.getId() &&
            email.type == SUGGEST_SECOND_PILLAR &&
            email.mandrillMessageId == messageId
    })
  }

  def 'calls correct methods when cancelling scheduled emails'() {
    given:
    User user = new User(id: 13)
    ScheduledEmailType type = SUGGEST_SECOND_PILLAR
    List<ScheduledEmail> emails = [
        new ScheduledEmail(userId: user.id, mandrillMessageId: "100", type: type),
        new ScheduledEmail(userId: user.id, mandrillMessageId: "200", type: type)
    ]

    when:
    service.cancel(user, type)

    then:
    1 * scheduledEmailRepository.findAllByUserIdAndTypeOrderByCreatedDateDesc(user.id, type) >> emails
    1 * emailService.cancelScheduledEmail("100")
    1 * emailService.cancelScheduledEmail("200")
    1 * scheduledEmailRepository.deleteAll(emails)
  }
}
