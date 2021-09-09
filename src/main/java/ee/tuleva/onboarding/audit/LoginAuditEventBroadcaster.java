package ee.tuleva.onboarding.audit;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoginAuditEventBroadcaster {

  private final AuditEventPublisher auditEventPublisher;

  @EventListener
  public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
    Person person = event.getPerson();
    log.info(
        "Broadcasting login audit event from BeforeTokenGrantedEvent: timestamp={}, personal code={}",
        event.getTimestamp(),
        person.getPersonalCode());

    Authentication auth = event.getAuthentication().getUserAuthentication();

    Object credentials = auth.getCredentials();
    if (GrantType.ID_CARD.equals(event.getGrantType())) {
      val idCardSession = (IdCardSession) credentials;
      auditEventPublisher.publish(
          person.getPersonalCode(),
          AuditEventType.LOGIN,
          "method=" + event.getGrantType(),
          "document=" + idCardSession.documentType);
    } else {
      auditEventPublisher.publish(
          person.getPersonalCode(), AuditEventType.LOGIN, "method=" + event.getGrantType());
    }
  }
}
