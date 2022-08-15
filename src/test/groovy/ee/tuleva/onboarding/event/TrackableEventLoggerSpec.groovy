package ee.tuleva.onboarding.event


import org.springframework.boot.actuate.security.AuthenticationAuditListener
import spock.lang.Specification

class TrackableEventLoggerSpec extends Specification {

    EventLogRepository eventLogRepository = Mock(EventLogRepository)
    TrackableEventLogger service = new TrackableEventLogger(eventLogRepository)

    def "OnTrackableEvent"() {
        given:
        TrackableEvent event = new TrackableEvent(
                "principal", "type", "data=true"
        )

        when:
        service.onTrackableEvent(event)
        then:

        1 * eventLogRepository.save({ EventLog eventLog ->
            eventLog.principal == event.auditEvent.principal &&
                    eventLog.type == event.auditEvent.type &&
                    eventLog.data == event.auditEvent.data
        })

    }

    def "OnTrackableEvent: Don't log authentication success events"() {
        given:
        TrackableEvent event = new TrackableEvent(
                "principal", AuthenticationAuditListener.AUTHENTICATION_SUCCESS, "data=true"
        )

        when:
        service.onTrackableEvent(event)
        then:

        0 * eventLogRepository.save({ EventLog eventLog ->
            eventLog.principal == event.auditEvent.principal &&
                    eventLog.type == event.auditEvent.type &&
                    eventLog.data == event.auditEvent.data
        })

    }
}
