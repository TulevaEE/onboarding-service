package ee.tuleva.onboarding.event.broadcasting

import ee.tuleva.onboarding.event.TrackableEventPublisher
import ee.tuleva.onboarding.event.TrackableEventType
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

class MandateSuccessfulEventBroadcasterSpec extends Specification {

  TrackableEventPublisher trackableEventPublisher = Mock(TrackableEventPublisher)
  MandateSuccessfulEventBroadcaster service = new MandateSuccessfulEventBroadcaster(trackableEventPublisher)

  def "Broadcast mandate successful event"() {
    given:
    def personalCode = '3762394717'
    User user = Mock({
      getPersonalCode() >> personalCode
    })
    Mandate mandate = Mock({
      getPillar() >> pillar
    })
    def event = new AfterMandateSignedEvent(new Object(), user, mandate, Locale.ENGLISH)

    when:
    service.publishMandateSuccessfulEvent(event)

    then:
    1 * trackableEventPublisher.publish(personalCode, TrackableEventType.MANDATE_SUCCESSFUL, eventData)

    where:
    pillar | eventData
    2      | "pillar=2"
    3      | "pillar=3"

  }
}
