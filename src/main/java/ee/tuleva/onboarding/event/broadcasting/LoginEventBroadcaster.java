package ee.tuleva.onboarding.event.broadcasting;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
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
    Person person = event.getPerson();

    if (GrantType.ID_CARD.equals(event.getAuthorizationGrantType())) {
      var documentType = event.getAttributes().getDocumentType().get();
      eventPublisher.publishEvent(
          new TrackableEvent(
              person,
              TrackableEventType.LOGIN,
              "method=" + event.getAuthorizationGrantType(),
              "document=" + documentType));
    } else {
      eventPublisher.publishEvent(
          new TrackableEvent(
              person, TrackableEventType.LOGIN, "method=" + event.getAuthorizationGrantType()));
    }
  }
}
