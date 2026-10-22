package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.APP_DATA_READ;

import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.oauth.AppDataRead;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppDataReadBroadcaster {

  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  public void onAppDataRead(AppDataRead event) {
    Map<String, @Nullable Object> data =
        Map.of(
            "clientId", event.clientId(),
            "grantId", event.grantId().toString(),
            "endpoint", event.endpoint(),
            "scopes", event.scopes());
    eventPublisher.publishEvent(new TrackableEvent(event.person(), APP_DATA_READ, data));
  }
}
