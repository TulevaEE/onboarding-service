package ee.tuleva.onboarding.event


import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

class TrackableEventPublisherSpec extends Specification {

    ApplicationEventPublisher applicationEventPublisher = Mock(ApplicationEventPublisher)
    TrackableEventPublisher trackableEventPublisher = new TrackableEventPublisher(applicationEventPublisher)

    def "Publish"() {
        given:
        String samplePrincipal = "principal"
        when:
        trackableEventPublisher.publish(samplePrincipal, TrackableEventType.LOGIN)
        then:
        1 * applicationEventPublisher.publishEvent({ TrackableEvent trackableEvent ->
            trackableEvent.auditEvent.principal == samplePrincipal &&
                trackableEvent.auditEvent.type == String.valueOf(TrackableEventType.LOGIN)
        })
    }

    def "Publish with data"() {
        given:
        String samplePrincipal = "principal"
        when:
        trackableEventPublisher.publish(samplePrincipal, TrackableEventType.LOGIN, "some=data")
        then:
        1 * applicationEventPublisher.publishEvent({ TrackableEvent trackableEvent ->
            trackableEvent.auditEvent.principal == samplePrincipal &&
                trackableEvent.auditEvent.type == String.valueOf(TrackableEventType.LOGIN) &&
                trackableEvent.auditEvent.data.get("some") == "data"
        })
    }

    def "Publish with data map"() {
      given:
      String samplePrincipal = "principal"
      Map<String, Object> data = new HashMap<>()
      String testKey = "test"
      String testValue = "value"
      data.put(testKey, testValue)
      when:
      trackableEventPublisher.publish(samplePrincipal, TrackableEventType.LOGIN, data)
      then:
      1 * applicationEventPublisher.publishEvent({ TrackableEvent trackableEvent ->
        trackableEvent.auditEvent.principal == samplePrincipal &&
            trackableEvent.auditEvent.type == String.valueOf(TrackableEventType.LOGIN) &&
            trackableEvent.auditEvent.data.get(testKey) == testValue
      })
    }
}
