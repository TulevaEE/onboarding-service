package ee.tuleva.onboarding.mandate.email.scheduledEmail

import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

class ScheduledEmailCancellerTest extends Specification {

  ScheduledEmailService scheduledEmailService = Mock()
  ScheduledEmailCanceller scheduledEmailCanceller = new ScheduledEmailCanceller(scheduledEmailService)

  def "cancels second pillar scheduled emails when mandate signed"() {
    given:
    User user = Mock()
    Mandate mandate = new Mandate(pillar: pillar)
    AfterMandateSignedEvent event = new AfterMandateSignedEvent(new Object(), user, mandate, Locale.ENGLISH)

    when:
    scheduledEmailCanceller.cancelEmail(event)

    then:
    callCount * scheduledEmailService.cancel(user, ScheduledEmailType.SUGGEST_SECOND_PILLAR)

    where:
    pillar | callCount
    2      | 1
    3      | 0
  }
}
