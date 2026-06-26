package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY;

public record CustodyRight(String childPersonalCode, Type type, boolean valid, boolean childAlive) {

  public enum Type {
    PERSONAL,
    PROPERTY,
    OTHER
  }

  public boolean grantsAssetManagement() {
    return type == PROPERTY && valid && childAlive;
  }
}
