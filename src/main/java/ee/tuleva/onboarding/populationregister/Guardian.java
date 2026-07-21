package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;

import ee.tuleva.onboarding.populationregister.CustodyRight.Type;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status;

public record Guardian(
    String personalCode, Type custodyType, CustodyValidity custodyValidity, Status personStatus) {

  public enum CustodyValidity {
    VALID,
    INVALID
  }

  public boolean grantsAssetManagement() {
    return custodyType == PROPERTY
        && custodyValidity == CustodyValidity.VALID
        && personStatus == ALIVE;
  }
}
