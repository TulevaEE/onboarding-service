package ee.tuleva.onboarding.event.anonymous;

import java.util.Set;
import org.jspecify.annotations.NullMarked;

// What a person who is not logged in may record, and the only data keys each event may carry.
@NullMarked
public enum AnonymousEventType {
  LOGIN_STARTED(Set.of("method")),
  LOGIN_FAILED(Set.of("method", "kind", "reason")),
  PAGE_VIEW(Set.of("path"));

  private final Set<String> allowedKeys;

  AnonymousEventType(Set<String> allowedKeys) {
    this.allowedKeys = allowedKeys;
  }

  public Set<String> allowedKeys() {
    return allowedKeys;
  }
}
