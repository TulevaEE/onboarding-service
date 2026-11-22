package ee.tuleva.onboarding.user;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ContactChange(
    ContactField field, @Nullable String oldValue, @Nullable String newValue, Instant changedAt) {

  public boolean onlyChangesLetterCase() {
    return AsciiLetterCase.equalIgnoringCase(oldValue, newValue);
  }

  @Override
  public String toString() {
    return "ContactChange[field=" + field + ", changedAt=" + changedAt + "]";
  }
}
