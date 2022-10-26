package ee.tuleva.onboarding.event


import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.event.TrackableEventType.LOGIN

class TrackableEventLoggerSpec extends Specification {

  EventLogRepository eventLogRepository = Mock(EventLogRepository)
  TrackableEventLogger service = new TrackableEventLogger(eventLogRepository)

  def "saves trackable events"() {
    given:
    TrackableEvent event = new TrackableEvent(samplePerson, LOGIN, "data=true")

    when:
    service.onTrackableEvent(event)

    then:
    1 * eventLogRepository.save({ EventLog eventLog ->
      eventLog.principal == event.auditEvent.principal
          && eventLog.type == event.auditEvent.type
          && eventLog.data == event.auditEvent.data
    })
  }
}
