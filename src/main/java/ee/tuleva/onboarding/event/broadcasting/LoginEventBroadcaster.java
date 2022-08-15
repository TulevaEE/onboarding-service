package ee.tuleva.onboarding.event.broadcasting;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.TrackableEventPublisher;
import ee.tuleva.onboarding.event.TrackableEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoginEventBroadcaster {

  private final TrackableEventPublisher trackableEventPublisher;

  @EventListener
  public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
    Person person = event.getPerson();
    Authentication auth = event.getAuthentication().getUserAuthentication();

    Object credentials = auth.getCredentials();
    if (GrantType.ID_CARD.equals(event.getGrantType())) {
      val idCardSession = (IdCardSession) credentials;
      trackableEventPublisher.publish(
          person.getPersonalCode(),
          TrackableEventType.LOGIN,
          "method=" + event.getGrantType(),
          "document=" + idCardSession.documentType);
    } else {
      trackableEventPublisher.publish(
          person.getPersonalCode(), TrackableEventType.LOGIN, "method=" + event.getGrantType());
    }
  }
}
