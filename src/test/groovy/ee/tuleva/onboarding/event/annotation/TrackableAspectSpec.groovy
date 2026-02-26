package ee.tuleva.onboarding.event.annotation

import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.event.TrackableEvent
import ee.tuleva.onboarding.event.TrackableEventType
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class TrackableAspectSpec extends Specification {

  ApplicationEventPublisher eventPublisher = Mock()
  TrackableAspect trackableAspect = new TrackableAspect(eventPublisher, JsonMapper.builder().build())

  def "tracks methods annotated with @Trackable annotation"() {
    given:
    Person person = samplePerson
    def methodParameterName1 = "methodParameterName1"
    def methodParameter1 = "methodParameter1"
    JoinPoint joinPoint = Mock({
      getSignature() >> Mock(MethodSignature, {
        getParameterNames() >> [methodParameterName1, "person"]
      })
      getArgs() >> [methodParameter1, person]
    })
    TrackableEventType eventType = TrackableEventType.PAYMENT_LINK
    Trackable trackable = Mock({
      value() >> eventType
    })

    when:
    trackableAspect.track(joinPoint, trackable, person)
    
    then:
    1 * eventPublisher.publishEvent(new TrackableEvent(person, eventType, [methodParameterName1: methodParameter1]))
  }
}
