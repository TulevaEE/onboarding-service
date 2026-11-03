package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.APP_CONNECTED;

import ee.tuleva.onboarding.auth.ClientConnection;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.oauth.AppConnected;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppConnectedBroadcaster {

  private final ApplicationEventPublisher eventPublisher;
  private final ClientConnection clientConnection;

  @EventListener
  public void onAppConnected(AppConnected event) {
    Map<String, @Nullable Object> data = new HashMap<>();
    data.put("clientId", event.clientId());
    data.put("scopes", event.scopes());
    data.putAll(RequestOrigin.readOnTheRequestThread(clientConnection));
    eventPublisher.publishEvent(new TrackableEvent(event.person(), APP_CONNECTED, data));
  }
}
