package ee.tuleva.onboarding.aml.notification;

public record AmlRiskLevelJobRunEvent(
    int highRiskRowCount,
    int mediumRiskRowCount,
    int totalRowsProcessed,
    int amlChecksCreatedCount) {

  public AmlRiskLevelJobRunEvent(
      int highRiskRowCount, int mediumRiskRowCount, int amlChecksCreatedCount) {
    this(
        highRiskRowCount,
        mediumRiskRowCount,
        highRiskRowCount + mediumRiskRowCount,
        amlChecksCreatedCount);
  }
}
