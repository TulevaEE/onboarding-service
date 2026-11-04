package ee.tuleva.onboarding.investment.event;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record RunSebPendingTransactionReconciliationBackfillRequested(
    @Nullable LocalDate resumeFrom) {

  public RunSebPendingTransactionReconciliationBackfillRequested() {
    this(null);
  }
}
