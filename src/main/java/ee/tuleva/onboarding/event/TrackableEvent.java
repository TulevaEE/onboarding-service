package ee.tuleva.onboarding.event;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.Map;
import lombok.EqualsAndHashCode;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;

@EqualsAndHashCode(callSuper = false)
public class TrackableEvent extends AuditApplicationEvent {

  public TrackableEvent(Person person, TrackableEventType type, String... data) {
    super(person.getPersonalCode(), String.valueOf(type), data);
  }

  public TrackableEvent(Person person, TrackableEventType type, Map<String, Object> data) {
    super(person.getPersonalCode(), String.valueOf(type), data);
  }
}
