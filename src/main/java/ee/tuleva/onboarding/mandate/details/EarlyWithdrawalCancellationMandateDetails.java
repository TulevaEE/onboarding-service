package ee.tuleva.onboarding.mandate.details;

import static ee.tuleva.onboarding.mandate.ApplicationType.CANCELLATION;

import ee.tuleva.onboarding.mandate.ApplicationType;
import ee.tuleva.onboarding.mandate.MandateType;

public class EarlyWithdrawalCancellationMandateDetails extends MandateDetails {
  public EarlyWithdrawalCancellationMandateDetails() {
    super(MandateType.EARLY_WITHDRAWAL_CANCELLATION);
  }

  @Override
  public ApplicationType getApplicationType() {
    return CANCELLATION;
  }
}
