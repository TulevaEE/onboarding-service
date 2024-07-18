package ee.tuleva.onboarding.epis.mandate.details;

import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import lombok.Getter;

public class WithdrawalCancellationMandateDetails extends MandateDetails {
  public WithdrawalCancellationMandateDetails() {
    super(MandateType.WITHDRAWAL_CANCELLATION);
  }
}
