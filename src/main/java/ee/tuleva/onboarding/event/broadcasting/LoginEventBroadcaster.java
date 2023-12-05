package ee.tuleva.onboarding.event.broadcasting;

import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoginEventBroadcaster {

  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
    Map<String, Object> data = new HashMap<>(event.getPerson().getAttributes());

    data.put("method", event.getGrantType());
    if (event.isIdCard()) {
      data.put("document", event.getIdDocumentType());
    }

    eventPublisher.publishEvent(
        new TrackableEvent(event.getPerson(), TrackableEventType.LOGIN, data));
  }
}
