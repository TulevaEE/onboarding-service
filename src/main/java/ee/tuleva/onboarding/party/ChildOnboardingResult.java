package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import jakarta.annotation.Nullable;
import java.time.LocalDate;

public record ChildOnboardingResult(
    boolean verified,
    @Nullable String firstName,
    @Nullable String lastName,
    @Nullable LocalDate dateOfBirth) {

  static ChildOnboardingResult verified(PopulationRegisterPerson child) {
    return new ChildOnboardingResult(
        true, child.firstName(), child.lastName(), child.dateOfBirth());
  }

  static ChildOnboardingResult underReview() {
    return new ChildOnboardingResult(false, null, null, null);
  }
}
