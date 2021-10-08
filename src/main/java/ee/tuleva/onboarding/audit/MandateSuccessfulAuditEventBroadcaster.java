package ee.tuleva.onboarding.audit;

import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MandateSuccessfulAuditEventBroadcaster {

  private final AuditEventPublisher auditEventPublisher;

  @EventListener
  public void publishMandateSuccessfulEvent(AfterMandateSignedEvent event) {
    auditEventPublisher.publish(
        event.getUser().getPersonalCode(),
        AuditEventType.MANDATE_SUCCESSFUL,
        "pillar=" + event.getPillar());
  }
}
