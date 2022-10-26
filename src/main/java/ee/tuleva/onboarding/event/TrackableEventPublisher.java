package ee.tuleva.onboarding.event;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class TrackableEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  public void publish(Person person, TrackableEventType type, String... data) {
    applicationEventPublisher.publishEvent(
        new TrackableEvent(person.getPersonalCode(), String.valueOf(type), data));
  }

  public void publish(Person person, TrackableEventType type, Map<String, Object> data) {
    applicationEventPublisher.publishEvent(
        new TrackableEvent(person.getPersonalCode(), String.valueOf(type), data));
  }
}
