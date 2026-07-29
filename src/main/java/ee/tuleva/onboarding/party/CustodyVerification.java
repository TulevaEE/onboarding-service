package ee.tuleva.onboarding.party;

import static java.util.Collections.unmodifiableMap;

import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import jakarta.annotation.Nullable;
import java.util.LinkedHashMap;
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

  public Map<String, Object> evidenceWithCitizenship() {
    if (child == null || child.citizenship() == null) {
      return evidence;
    }
    var enriched = new LinkedHashMap<String, Object>(evidence);
    enriched.put("citizenship", child.citizenship());
    return unmodifiableMap(enriched);
  }

  static CustodyVerification notVerified(Outcome outcome, Map<String, Object> evidence) {
    return new CustodyVerification(outcome, null, evidence);
  }
}
