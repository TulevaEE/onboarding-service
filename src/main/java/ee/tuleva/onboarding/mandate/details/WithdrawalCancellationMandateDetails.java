package ee.tuleva.onboarding.mandate.details;

import static ee.tuleva.onboarding.mandate.ApplicationType.CANCELLATION;

import ee.tuleva.onboarding.mandate.ApplicationType;
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
