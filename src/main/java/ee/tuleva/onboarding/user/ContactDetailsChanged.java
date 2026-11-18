package ee.tuleva.onboarding.user;

import java.util.List;

public record ContactDetailsChanged(
    User user, ContactChangeSource source, List<ContactChange> changes) {

  public ContactDetailsChanged {
    if (changes.isEmpty()) {
      throw new IllegalArgumentException("Contact details changed without changes");
    }
    changes = List.copyOf(changes);
  }
}
