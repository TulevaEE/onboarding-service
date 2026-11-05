package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import java.time.LocalDate;

class SebReconciliationLockLostException extends JobBusyException {

  SebReconciliationLockLostException(LocalDate reportDate) {
    super(
        "SEB reconciliation lost its lock, the report is not committed: reportDate="
            + reportDate
            + ", lock="
            + SebReconciliationLock.NAME);
  }
}
