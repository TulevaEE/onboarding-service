package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record PopulationRegisterPerson(
    String personalCode,
    String firstName,
    String lastName,
    @Nullable LocalDate dateOfBirth,
    Status status,
    @Nullable String citizenship) {

  public enum Status {
    ALIVE,
    INACTIVE,
    UNKNOWN
  }

  public boolean isAlive() {
    return status == ALIVE;
  }
}
