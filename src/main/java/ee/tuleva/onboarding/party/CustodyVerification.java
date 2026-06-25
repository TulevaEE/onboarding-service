package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import jakarta.annotation.Nullable;
import java.util.Map;

public record CustodyVerification(
    Outcome outcome, @Nullable PopulationRegisterPerson child, Map<String, Object> evidence) {

  public enum Outcome {
    OK,
    NO_CUSTODY,
    NOT_ASSET_MANAGEMENT,
    CHILD_NOT_ALIVE
  }

  public boolean isVerified() {
    return outcome == Outcome.OK;
  }

  static CustodyVerification notVerified(Outcome outcome) {
    return new CustodyVerification(outcome, null, Map.of());
  }
}
