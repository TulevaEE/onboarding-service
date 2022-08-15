package ee.tuleva.onboarding.event.broadcasting;

import ee.tuleva.onboarding.event.TrackableEventPublisher;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MandateSuccessfulEventBroadcaster {

  private final TrackableEventPublisher trackableEventPublisher;

  @EventListener
  public void publishMandateSuccessfulEvent(AfterMandateSignedEvent event) {
    trackableEventPublisher.publish(
        event.getUser().getPersonalCode(),
        TrackableEventType.MANDATE_SUCCESSFUL,
        "pillar=" + event.getPillar());
  }
}
