package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;

import org.jspecify.annotations.Nullable;

public record CustodyRight(
    String childPersonalCode,
    Type type,
    boolean valid,
    boolean childAlive,
    @Nullable String firstName,
    @Nullable String lastName) {

  // The register does not always populate the other person's name, so the eligible-children
  // listing must tolerate a missing one; the custody decision itself never depends on it.
  public CustodyRight(String childPersonalCode, Type type, boolean valid, boolean childAlive) {
    this(childPersonalCode, type, valid, childAlive, null, null);
  }

  public enum Type {
    PERSONAL_CUSTODY,
    PROPERTY_CUSTODY,
    OTHER
  }

  public boolean grantsAssetManagement() {
    return type == PROPERTY_CUSTODY && valid && childAlive;
  }
}
