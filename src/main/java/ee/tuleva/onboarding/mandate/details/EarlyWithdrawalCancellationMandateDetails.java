package ee.tuleva.onboarding.mandate.details;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.CANCELLATION;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
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
