package ee.tuleva.onboarding.mandate.email.scheduledEmail

import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType.SUGGEST_SECOND_PILLAR

class ScheduledEmailServiceSpec extends Specification {

  ScheduledEmailRepository scheduledEmailRepository = Mock()
  ScheduledEmailService service = new ScheduledEmailService(scheduledEmailRepository)

  def 'creates scheduled email with correct attributes'() {
    given:
    User user = User.builder().id(13L).build();
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
}
