package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.CANCELLATION;

import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.application.ApplicationType;

public class WithdrawalCancellationMandateDetails extends MandateDetails {
  public WithdrawalCancellationMandateDetails() {
    super(MandateType.WITHDRAWAL_CANCELLATION);
  }

  @Override
  public ApplicationType getApplicationType() {
    return CANCELLATION;
  }
}
