package ee.tuleva.onboarding.aml.notification;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AmlRiskLevelJobRunEvent extends ApplicationEvent {

  private final int highRiskRowCount;
  private final int amlChecksCreatedCount;

  public AmlRiskLevelJobRunEvent(Object source, int highRiskRowCount, int amlChecksCreatedCount) {
    super(source);
    this.amlChecksCreatedCount = amlChecksCreatedCount;
    this.highRiskRowCount = highRiskRowCount;
  }
}
