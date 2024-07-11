package ee.tuleva.onboarding.epis.mandate.details;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.EnumSet;

public class CancellationMandateDetails implements MandateDetails {

  private static EnumSet<ApplicationType> PERMITTED_APPLICATION_TYPES = EnumSet.of(
      ApplicationType.TRANSFER,
      ApplicationType.SELECTION,
      ApplicationType.EARLY_WITHDRAWAL,
      ApplicationType.WITHDRAWAL,
      ApplicationType.PAYMENT,
      ApplicationType.PAYMENT_RATE
  );

  @NotNull
  @Getter
  private ApplicationType applicationTypeToCancel;

  public CancellationMandateDetails(ApplicationType type) {
    if (!PERMITTED_APPLICATION_TYPES.contains(type)) {
      throw new IllegalArgumentException("Unsupported application type: " + type);
    }

    this.applicationTypeToCancel = type;
  }

  @Override
  public ApplicationType getType() {
    return null;
  }
}
