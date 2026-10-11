package ee.tuleva.onboarding.instrument;

import java.util.List;

public record InstrumentRetirementOutcome(
    List<String> retiredIsins, List<Refusal> refusals, boolean cacheReloadedOnThisInstance) {

  public boolean isEmpty() {
    return retiredIsins.isEmpty() && refusals.isEmpty();
  }

  public boolean retiredWithoutReloadingThisInstance() {
    return !retiredIsins.isEmpty() && !cacheReloadedOnThisInstance;
  }

  public record Refusal(String isin, String reason) {

    public String describe() {
      return "%s — %s".formatted(isin, reason);
    }
  }
}
