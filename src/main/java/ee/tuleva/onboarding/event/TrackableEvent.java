package ee.tuleva.onboarding.event;

import java.util.Map;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;

public class TrackableEvent extends AuditApplicationEvent {

  public TrackableEvent(String principal, String type, String... data) {
    super(principal, type, data);
  }

  public TrackableEvent(String principal, String type, Map<String, Object> data) {
    super(principal, type, data);
  }
}
