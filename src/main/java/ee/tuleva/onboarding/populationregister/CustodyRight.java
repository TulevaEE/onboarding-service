package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;

import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status;
import org.jspecify.annotations.Nullable;

public record CustodyRight(
    String childPersonalCode,
    Type type,
    CustodyValidity custodyValidity,
    Status childStatus,
    @Nullable String firstName,
    @Nullable String lastName) {

  // The register does not always populate the other person's name, so the eligible-children
  // listing must tolerate a missing one; the custody decision itself never depends on it.
  public CustodyRight(
      String childPersonalCode, Type type, CustodyValidity custodyValidity, Status childStatus) {
    this(childPersonalCode, type, custodyValidity, childStatus, null, null);
  }

  public enum Type {
    PERSONAL_CUSTODY,
    PROPERTY_CUSTODY,
    OTHER
  }

  public boolean valid() {
    return custodyValidity == VALID;
  }

  public boolean childAlive() {
    return childStatus == ALIVE;
  }

  public boolean grantsAssetManagement() {
    return type == PROPERTY_CUSTODY && valid() && childAlive();
  }
}
