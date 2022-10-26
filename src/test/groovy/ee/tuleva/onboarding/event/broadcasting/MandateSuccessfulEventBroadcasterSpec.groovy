package ee.tuleva.onboarding.event.broadcasting

import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.event.TrackableEventPublisher
import ee.tuleva.onboarding.event.TrackableEventType
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateFixture
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class MandateSuccessfulEventBroadcasterSpec extends Specification {

  TrackableEventPublisher trackableEventPublisher = Mock(TrackableEventPublisher)
  MandateSuccessfulEventBroadcaster service = new MandateSuccessfulEventBroadcaster(trackableEventPublisher)

  def "Broadcast mandate successful event"() {
    given:
    User user = sampleUser
    Mandate mandate = sampleMandate().tap { it.pillar = pillar }
    def event = new AfterMandateSignedEvent(this, user, mandate, Locale.ENGLISH)

    when:
    service.publishMandateSuccessfulEvent(event)

    then:
    1 * trackableEventPublisher.publish(user, TrackableEventType.MANDATE_SUCCESSFUL, eventData)

    where:
    pillar | eventData
    2      | "pillar=2"
    3      | "pillar=3"

  }
}
