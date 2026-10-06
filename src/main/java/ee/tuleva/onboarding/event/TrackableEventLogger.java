package ee.tuleva.onboarding.event;

import static ee.tuleva.onboarding.event.TrackableEvent.IP_ADDRESS;
import static ee.tuleva.onboarding.event.TrackableEvent.USER_AGENT;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrackableEventLogger {

  private final EventLogRepository eventLogRepository;

  @EventListener
  public void onTrackableEvent(TrackableEvent trackableEvent) {
    logEvent(trackableEvent.getAuditEvent());
  }

  @EventListener
  public void onTrackableSystemEvent(TrackableSystemEvent trackableSystemEvent) {
    logEvent(trackableSystemEvent.getAuditEvent());
  }

  private void logEvent(AuditEvent event) {
    log.info(
        "Logging event: timestamp={}, principal={}, type={}, data={}",
        event.getTimestamp(),
        event.getPrincipal(),
        event.getType(),
        withoutClientConnection(event.getData()));

    eventLogRepository.save(
        EventLog.builder()
            .type(event.getType())
            .principal(event.getPrincipal())
            .timestamp(event.getTimestamp())
            .data(event.getData())
            .build());
  }

  private static Map<String, @Nullable Object> withoutClientConnection(
      Map<String, @Nullable Object> data) {
    Map<String, @Nullable Object> loggable = new HashMap<>(data);
    loggable.remove(IP_ADDRESS);
    loggable.remove(USER_AGENT);
    return loggable;
  }
}
