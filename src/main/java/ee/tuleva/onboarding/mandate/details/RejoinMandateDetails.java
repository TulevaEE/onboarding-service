package ee.tuleva.onboarding.mandate.details;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.mandate.MandateType;

public class RejoinMandateDetails extends MandateDetails {

  public RejoinMandateDetails() {
    super(MandateType.REJOIN);
  }

  @Override
  public ApplicationType getApplicationType() {
    return ApplicationType.REJOIN;
  }
}
