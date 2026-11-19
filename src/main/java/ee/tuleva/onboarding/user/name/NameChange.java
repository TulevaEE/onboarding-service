package ee.tuleva.onboarding.user.name;

import ee.tuleva.onboarding.user.NameChangeBasis;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record NameChange(
    @Nullable String oldFirstName,
    @Nullable String oldLastName,
    String newFirstName,
    String newLastName,
    NameChangeBasis basis,
    Instant changedAt) {

  @Override
  public String toString() {
    return "NameChange[source=" + basis.source() + ", changedAt=" + changedAt + "]";
  }
}
