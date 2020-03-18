package ee.tuleva.onboarding.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class AuditEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  public void publish(String principal, AuditEventType type, String... data) {
    log.info("Publishing AuditEvent, principal {}, type: {}", principal, type);

    applicationEventPublisher.publishEvent(
        new AuditApplicationEvent(new AuditEvent(principal, String.valueOf(type), data)));
  }
}
