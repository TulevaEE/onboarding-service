package ee.tuleva.onboarding.event.broadcasting;

import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MandateSuccessfulEventBroadcaster {

  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  public void publishMandateSuccessfulEvent(AfterMandateSignedEvent event) {
    eventPublisher.publishEvent(
        new TrackableEvent(
            event.user(),
            TrackableEventType.MANDATE_SUCCESSFUL,
            Map.of("pillar", event.getPillar())));
  }
}
