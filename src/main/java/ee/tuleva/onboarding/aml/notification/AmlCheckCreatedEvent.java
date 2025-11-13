package ee.tuleva.onboarding.aml.notification;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckType;
import org.springframework.context.ApplicationEvent;

public class AmlCheckCreatedEvent extends ApplicationEvent {

  private final AmlCheck amlCheck;

  public AmlCheckCreatedEvent(Object source, AmlCheck amlCheck) {
    super(source);
    this.amlCheck = amlCheck;
  }

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
