package ee.tuleva.onboarding.aml.notification;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AmlChecksRunEvent extends ApplicationEvent {

  private final int numberOfRecords;

  public AmlChecksRunEvent(Object source, int numberOfRecords) {
    super(source);
    this.numberOfRecords = numberOfRecords;
  }
}
