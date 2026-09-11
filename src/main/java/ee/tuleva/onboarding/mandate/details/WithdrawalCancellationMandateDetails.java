package ee.tuleva.onboarding.mandate.details;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.CANCELLATION;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.mandate.MandateType;

public class WithdrawalCancellationMandateDetails extends MandateDetails {
  public WithdrawalCancellationMandateDetails() {
    super(MandateType.WITHDRAWAL_CANCELLATION);
  }

  @Override
  public ApplicationType getApplicationType() {
    return CANCELLATION;
  }
}
