package ee.tuleva.onboarding.event.annotation

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.event.TrackableEventPublisher
import ee.tuleva.onboarding.event.TrackableEventType
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.reflect.MethodSignature
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class TrackableAspectSpec extends Specification {

  TrackableEventPublisher eventPublisher = Mock()
  TrackableAspect trackableAspect = new TrackableAspect(eventPublisher, new ObjectMapper())

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
    1 * eventPublisher.publish(person, eventType, [methodParameterName1: methodParameter1])
  }
}
