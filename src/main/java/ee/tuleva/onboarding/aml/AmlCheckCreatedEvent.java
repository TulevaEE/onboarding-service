package ee.tuleva.onboarding.aml;

import org.springframework.context.ApplicationEvent;

public class AmlCheckCreatedEvent extends ApplicationEvent {

  private final AmlCheck amlCheck;

  public AmlCheckCreatedEvent(Object source, AmlCheck amlCheck) {
    super(source);
    this.amlCheck = amlCheck;
  }
}
