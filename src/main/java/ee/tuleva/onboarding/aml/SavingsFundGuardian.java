package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record SavingsFundGuardian(
    String personalCode, @Nullable String firstName, @Nullable String lastName) {

  public boolean hasRecordedName() {
    return firstName != null && lastName != null;
  }

  Optional<Person> recordedPerson() {
    if (firstName == null || lastName == null) {
      return Optional.empty();
    }
    return Optional.of(new PersonImpl(personalCode, firstName, lastName));
  }
}
