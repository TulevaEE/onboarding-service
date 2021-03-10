package ee.tuleva.onboarding.audit;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.boot.actuate.security.AuthenticationAuditListener;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditApplicationEventLogger {

  private final AuditLogRepository auditLogRepository;

  @EventListener
  public void onAuditEvent(AuditApplicationEvent event) {
    AuditEvent auditEvent = event.getAuditEvent();

    if (Objects.equals(auditEvent.getType(), AuthenticationAuditListener.AUTHENTICATION_SUCCESS)) {
      return;
    }

    log.info(
        "Logging audit application event: timestamp={}, principal={}, type={}, data={}",
        auditEvent.getTimestamp(),
        auditEvent.getPrincipal(),
        auditEvent.getType(),
        auditEvent.getData());

    auditLogRepository.save(
        AuditLog.builder()
            .type(auditEvent.getType())
            .principal(auditEvent.getPrincipal())
            .timestamp(auditEvent.getTimestamp())
            .data(auditEvent.getData())
            .build());
  }
}
