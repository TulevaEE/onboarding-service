package ee.tuleva.onboarding.investment.event;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record RunSebPendingTransactionReconciliationBackfillRequested(
    @Nullable LocalDate resumeFrom) {

  public static final String JOB_NAME = "SebPendingTransactionReconciliationBackfillJob";

  public RunSebPendingTransactionReconciliationBackfillRequested() {
    this(null);
  }
}
