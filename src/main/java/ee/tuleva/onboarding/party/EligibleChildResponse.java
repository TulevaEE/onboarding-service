package ee.tuleva.onboarding.party;

import org.jspecify.annotations.Nullable;

public record EligibleChildResponse(
    String personalCode,
    @Nullable String firstName,
    @Nullable String lastName,
    boolean hasBeenOnboarded) {

  EligibleChildResponse(EligibleChild child) {
    this(child.personalCode(), child.firstName(), child.lastName(), child.hasBeenOnboarded());
  }
}
