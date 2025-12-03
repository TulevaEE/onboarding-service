package ee.tuleva.onboarding.mandate.email.persistence

import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

class ScheduledEmailCancellerSpec extends Specification {

  EmailPersistenceService emailPersistenceService = Mock()
  ScheduledEmailCanceller scheduledEmailCanceller = new ScheduledEmailCanceller(emailPersistenceService)

  def "cancels second pillar scheduled emails when mandate signed"() {
    given:
    User user = Mock()
    Mandate mandate = new Mandate(pillar: pillar)
    AfterMandateSignedEvent event = new AfterMandateSignedEvent(user, mandate, Locale.ENGLISH)

    when:
    scheduledEmailCanceller.cancelEmail(event)

    then:
    callCount * emailPersistenceService.cancel(user, EmailType.THIRD_PILLAR_SUGGEST_SECOND)

    where:
    pillar | callCount
    2      | 1
    3      | 0
  }
}
