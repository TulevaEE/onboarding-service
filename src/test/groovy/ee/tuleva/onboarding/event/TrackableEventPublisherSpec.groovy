package ee.tuleva.onboarding.event


import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class TrackableEventPublisherSpec extends Specification {

  ApplicationEventPublisher applicationEventPublisher = Mock(ApplicationEventPublisher)
  TrackableEventPublisher trackableEventPublisher = new TrackableEventPublisher(applicationEventPublisher)

  def "Publish"() {
    given:
    Person person = samplePerson
    when:
    trackableEventPublisher.publish(person, TrackableEventType.LOGIN)
    then:
    1 * applicationEventPublisher.publishEvent({ TrackableEvent trackableEvent ->
      trackableEvent.auditEvent.principal == person.personalCode &&
          trackableEvent.auditEvent.type == String.valueOf(TrackableEventType.LOGIN)
    })
  }

  def "Publish with data"() {
    given:
    Person person = samplePerson
    when:
    trackableEventPublisher.publish(person, TrackableEventType.LOGIN, "some=data")
    then:
    1 * applicationEventPublisher.publishEvent({ TrackableEvent trackableEvent ->
      trackableEvent.auditEvent.principal == person.personalCode &&
          trackableEvent.auditEvent.type == String.valueOf(TrackableEventType.LOGIN) &&
          trackableEvent.auditEvent.data["some"] == "data"
    })
  }

  def "Publish with data map"() {
    given:
    Person person = samplePerson
    Map<String, Object> data = new HashMap<>()
    String testKey = "test"
    String testValue = "value"
    data.put(testKey, testValue)
    when:
    trackableEventPublisher.publish(person, TrackableEventType.LOGIN, data)
    then:
    1 * applicationEventPublisher.publishEvent({ TrackableEvent trackableEvent ->
      trackableEvent.auditEvent.principal == person.personalCode &&
          trackableEvent.auditEvent.type == String.valueOf(TrackableEventType.LOGIN) &&
          trackableEvent.auditEvent.data[testKey] == testValue
    })
  }
}
