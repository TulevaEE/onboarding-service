package ee.tuleva.onboarding.event;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.security.AuthenticationAuditListener;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrackableEventLogger {

  private final EventLogRepository eventLogRepository;

  @EventListener
  public void onTrackableEvent(TrackableEvent trackableEvent) {
    AuditEvent event = trackableEvent.getAuditEvent();

    if (Objects.equals(event.getType(), AuthenticationAuditListener.AUTHENTICATION_SUCCESS)) {
      return;
    }

    log.info(
        "Logging event: timestamp={}, principal={}, type={}, data={}",
        event.getTimestamp(),
        event.getPrincipal(),
        event.getType(),
        event.getData());

    eventLogRepository.save(
        EventLog.builder()
            .type(event.getType())
            .principal(event.getPrincipal())
            .timestamp(event.getTimestamp())
            .data(event.getData())
            .build());
  }
}
