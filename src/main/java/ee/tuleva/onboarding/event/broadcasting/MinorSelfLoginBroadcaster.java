package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.MINOR_SELF_LOGIN;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.event.TrackableEvent;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinorSelfLoginBroadcaster {

  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @EventListener
  public void onAfterTokenGranted(AfterTokenGrantedEvent event) {
    if (!event.getPerson().isMinorActingAsSelf(LocalDate.now(clock))) {
      return;
    }
    eventPublisher.publishEvent(
        new TrackableEvent(
            event.getPerson(), MINOR_SELF_LOGIN, Map.of("method", event.getGrantType())));
  }
}
