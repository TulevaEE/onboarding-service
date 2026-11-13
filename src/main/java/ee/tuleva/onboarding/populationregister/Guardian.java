package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;

import ee.tuleva.onboarding.populationregister.CustodyRight.Type;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status;
import org.jspecify.annotations.Nullable;

public record Guardian(
    String personalCode,
    Type custodyType,
    CustodyValidity custodyValidity,
    Status personStatus,
    @Nullable String firstName,
    @Nullable String lastName) {

  public boolean grantsAssetManagement() {
    return custodyType == PROPERTY_CUSTODY && custodyValidity == VALID && personStatus == ALIVE;
  }
}
