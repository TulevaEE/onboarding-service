package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;

record UnitHoldingChange(
    UUID transactionId, TransactionType transactionType, BigDecimal units, BigDecimal cost) {

  UnitHoldingChange recordingOperation(TransactionType operationType) {
    return new UnitHoldingChange(transactionId, operationType, units, cost);
  }
}
