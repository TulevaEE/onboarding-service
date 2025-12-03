package ee.tuleva.onboarding.event.broadcasting

import ee.tuleva.onboarding.event.TrackableEvent
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.user.User
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.event.TrackableEventType.MANDATE_SUCCESSFUL
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class MandateSuccessfulEventBroadcasterSpec extends Specification {

  ApplicationEventPublisher eventPublisher = Mock()
  MandateSuccessfulEventBroadcaster service = new MandateSuccessfulEventBroadcaster(eventPublisher)

  def "Broadcast mandate successful event"() {
    given:
    User user = sampleUser
    Mandate mandate = sampleMandate().tap { it.pillar = pillar }
    def event = new AfterMandateSignedEvent(user, mandate, Locale.ENGLISH)

    when:
    service.publishMandateSuccessfulEvent(event)

    then:
    1 * eventPublisher.publishEvent(new TrackableEvent(user, MANDATE_SUCCESSFUL, eventData))

    where:
    pillar | eventData
    2      | [pillar: 2]
    3      | [pillar: 3]
  }
}
