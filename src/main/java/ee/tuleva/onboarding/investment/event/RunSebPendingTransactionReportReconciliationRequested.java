package ee.tuleva.onboarding.investment.event;

import java.time.LocalDate;

public record RunSebPendingTransactionReportReconciliationRequested(LocalDate reportDate) {

  public static final String JOB_NAME = "SebPendingTransactionReportReconciliationJob";

  public String jobTriggerName() {
    return JOB_NAME + ":" + reportDate;
  }
}
