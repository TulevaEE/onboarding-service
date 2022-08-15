package ee.tuleva.onboarding.event;

import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;

public class TrackableEvent extends AuditApplicationEvent {

  public TrackableEvent(String principal, String type, String... data) {
    super(principal, type, data);
  }
}
