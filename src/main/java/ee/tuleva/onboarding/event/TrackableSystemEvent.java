package ee.tuleva.onboarding.event;

import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;

@Getter
@EqualsAndHashCode(callSuper = false)
public class TrackableSystemEvent extends AuditApplicationEvent {

  private static final String SYSTEM_PRINCIPAL = "onboarding-service";

  private final TrackableEventType type;
  private final Map<String, Object> data;

  public TrackableSystemEvent(TrackableEventType type, Map<String, Object> data) {
    super(SYSTEM_PRINCIPAL, String.valueOf(type), data);
    this.type = type;
    this.data = new HashMap<>(data);
  }
}
