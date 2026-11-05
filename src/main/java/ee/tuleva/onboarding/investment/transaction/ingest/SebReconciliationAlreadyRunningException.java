package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.event.JobBusyException;

public class SebReconciliationAlreadyRunningException extends JobBusyException {

  public SebReconciliationAlreadyRunningException(String lockName) {
    super("SEB pending transaction reconciliation skipped, its lock is held: lock=" + lockName);
  }
}
