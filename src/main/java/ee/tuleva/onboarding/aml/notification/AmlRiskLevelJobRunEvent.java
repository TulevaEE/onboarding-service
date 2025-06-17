package ee.tuleva.onboarding.aml.notification;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AmlRiskLevelJobRunEvent extends ApplicationEvent {

  private final int highRiskRowCount;
  private final int mediumRiskRowCount;
  private final int totalRowsProcessed;
  private final int amlChecksCreatedCount;

  public AmlRiskLevelJobRunEvent(
      Object source, int highRiskRowCount, int mediumRiskRowCount, int amlChecksCreatedCount) {
    super(source);
    this.highRiskRowCount = highRiskRowCount;
    this.mediumRiskRowCount = mediumRiskRowCount;
    this.totalRowsProcessed = highRiskRowCount + mediumRiskRowCount;
    this.amlChecksCreatedCount = amlChecksCreatedCount;
  }
}
