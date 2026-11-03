package ee.tuleva.onboarding.investment.transaction.ingest;

public class SebReconciliationAlreadyRunningException extends IllegalStateException {

  public SebReconciliationAlreadyRunningException(String lockName) {
    super("SEB pending transaction reconciliation skipped, its lock is held: lock=" + lockName);
  }
}
