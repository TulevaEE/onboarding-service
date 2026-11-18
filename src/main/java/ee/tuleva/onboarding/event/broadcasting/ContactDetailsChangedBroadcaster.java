package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.CONTACT_DETAILS_CHANGE;

import ee.tuleva.onboarding.auth.ClientConnection;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactDetailsChanged;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContactDetailsChangedBroadcaster {

  private final ApplicationEventPublisher eventPublisher;
  private final ClientConnection clientConnection;

  @EventListener
  public void onContactDetailsChanged(ContactDetailsChanged event) {
    Map<String, @Nullable Object> data = new HashMap<>();
    data.put("fields", event.changes().stream().map(ContactChange::field).map(Enum::name).toList());
    data.put("source", event.source().name());
    data.putAll(RequestOrigin.readOnTheRequestThread(clientConnection));
    eventPublisher.publishEvent(new TrackableEvent(event.user(), CONTACT_DETAILS_CHANGE, data));
  }
}
