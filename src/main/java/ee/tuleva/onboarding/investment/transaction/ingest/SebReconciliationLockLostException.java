package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.LocalDate;

class SebReconciliationLockLostException extends IllegalStateException {

  SebReconciliationLockLostException(LocalDate reportDate) {
    super(
        "SEB reconciliation lost its lock, the report is not committed: reportDate="
            + reportDate
            + ", lock="
            + SebReconciliationLock.NAME);
  }
}
