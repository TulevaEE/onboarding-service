package ee.tuleva.onboarding.mandate.details;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.CANCELLATION;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.mandate.MandateType;

public class RejoinCancellationMandateDetails extends MandateDetails {

  public RejoinCancellationMandateDetails() {
    super(MandateType.REJOIN_CANCELLATION);
  }

  @Override
  public ApplicationType getApplicationType() {
    return CANCELLATION;
  }
}
