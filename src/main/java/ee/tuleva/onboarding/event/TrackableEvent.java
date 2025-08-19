package ee.tuleva.onboarding.event;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;

@Getter
@EqualsAndHashCode(callSuper = false)
public class TrackableEvent extends AuditApplicationEvent {

  private final Person person;
  private final TrackableEventType type;
  private final Map<String, Object> data;

  public TrackableEvent(Person person, TrackableEventType type) {
    this(person, type, Map.of());
  }

  public TrackableEvent(Person person, TrackableEventType type, Map<String, Object> data) {
    super(person.getPersonalCode(), String.valueOf(type), data);
    this.person = person;
    this.type = type;
    this.data = new HashMap<>(data);
  }
}
