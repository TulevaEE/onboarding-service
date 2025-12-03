package ee.tuleva.onboarding.aml.notification;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckType;

public record AmlCheckCreatedEvent(AmlCheck amlCheck) {

  public AmlCheckType getAmlCheckType() {
    return amlCheck.getType();
  }

  public boolean isFailed() {
    return !amlCheck.isSuccess();
  }

  public Long getCheckId() {
    return amlCheck.getId();
  }
}
