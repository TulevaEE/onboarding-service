package ee.tuleva.onboarding.event;

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

  public void publish(String principal, TrackableEventType type, String... data) {
    log.info("Publishing Event: principal={}, type={}", principal, type);

    applicationEventPublisher.publishEvent(
        new TrackableEvent(principal, String.valueOf(type), data));
  }

  public void publish(String principal, TrackableEventType type, Map<String, Object> data) {
    log.info("Publishing Event: principal={}, type={}", principal, type);

    applicationEventPublisher.publishEvent(
        new TrackableEvent(principal, String.valueOf(type), data));
  }
}
