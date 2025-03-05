package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.CANCELLATION;

import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.application.ApplicationType;

public class EarlyWithdrawalCancellationMandateDetails extends MandateDetails {
  public EarlyWithdrawalCancellationMandateDetails() {
    super(MandateType.EARLY_WITHDRAWAL_CANCELLATION);
  }

  @Override
  public ApplicationType getApplicationType() {
    return CANCELLATION;
  }
}
