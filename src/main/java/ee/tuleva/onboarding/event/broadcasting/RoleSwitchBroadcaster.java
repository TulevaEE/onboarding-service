package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.ROLE_SWITCH;

import ee.tuleva.onboarding.auth.event.RoleSwitchedEvent;
import ee.tuleva.onboarding.event.TrackableEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleSwitchBroadcaster {

  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  public void onRoleSwitched(RoleSwitchedEvent event) {
    eventPublisher.publishEvent(
        new TrackableEvent(
            event.originalPerson(),
            ROLE_SWITCH,
            Map.of(
                "roleType", event.person().getRoleType().name(),
                "code", event.person().getRoleCode())));
  }
}
