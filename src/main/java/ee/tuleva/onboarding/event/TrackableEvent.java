package ee.tuleva.onboarding.event;

import java.util.Map;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;

public class TrackableEvent extends AuditApplicationEvent {

  public TrackableEvent(String personalCode, String type, String... data) {
    super(personalCode, type, data);
  }

  public TrackableEvent(String personalCode, String type, Map<String, Object> data) {
    super(personalCode, type, data);
  }
}
