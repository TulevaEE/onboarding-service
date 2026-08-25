package ee.tuleva.onboarding.party;

import static java.util.Collections.unmodifiableMap;

import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record CustodyVerification(
    Outcome outcome, @Nullable PopulationRegisterPerson child, Map<String, Object> evidence) {

  static final String CITIZENSHIP = "citizenship";
  static final String CITIZENSHIPS = "citizenships";

  public enum Outcome {
    OK,
    NO_CUSTODY,
    NOT_ASSET_MANAGEMENT,
    CHILD_NOT_ALIVE
  }

  public boolean isVerified() {
    return outcome == Outcome.OK;
  }

  public Map<String, Object> evidenceWithCitizenships() {
    if (child == null) {
      return evidence;
    }
    var enriched = new LinkedHashMap<String, Object>(evidence);
    if (child.citizenship() != null) {
      enriched.put(CITIZENSHIP, child.citizenship());
    }
    enriched.put(CITIZENSHIPS, child.citizenships());
    return unmodifiableMap(enriched);
  }

  static CustodyVerification notVerified(Outcome outcome, Map<String, Object> evidence) {
    return new CustodyVerification(outcome, null, evidence);
  }
}
