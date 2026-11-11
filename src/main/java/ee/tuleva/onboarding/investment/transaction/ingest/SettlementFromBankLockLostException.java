package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.event.JobBusyException;

class SettlementFromBankLockLostException extends JobBusyException {

  SettlementFromBankLockLostException(Long nextOrderId) {
    super(
        "Settling from the bank lost the reconciliation lock, stopped before the next order:"
            + " nextOrderId="
            + nextOrderId
            + ", lock="
            + SebReconciliationLock.NAME);
  }
}
