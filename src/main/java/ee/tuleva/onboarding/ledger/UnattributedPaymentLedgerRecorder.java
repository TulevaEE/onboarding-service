package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_BOUNCE_BACK;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNATTRIBUTED_PAYMENT;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class UnattributedPaymentLedgerRecorder {

  private final SavingsFundLedgerAccounts accounts;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  @Transactional
  LedgerTransaction recordUnattributedPayment(BigDecimal amount, UUID externalReference) {
    return recordUnattributedPayment(amount, externalReference, LocalDate.now(clock));
  }

  @Transactional
  LedgerTransaction recordUnattributedPayment(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    LedgerAccount unreconciledAccount = accounts.getUnreconciledBankReceiptsAccount();
    LedgerAccount incomingPaymentsAccount = accounts.getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), UNATTRIBUTED_PAYMENT.name());

    return ledgerTransactionService.createTransaction(
        UNATTRIBUTED_PAYMENT,
        accounts.transactionDate(bookingDate),
        externalReference,
        metadata,
        accounts.entry(incomingPaymentsAccount, amount),
        accounts.entry(unreconciledAccount, amount.negate()));
  }

  @Transactional
  LedgerTransaction bounceBackUnattributedPayment(BigDecimal amount, UUID externalReference) {
    var existing =
        ledgerTransactionService.findByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_BOUNCE_BACK);
    if (existing.isPresent()) {
      log.error(
          "Duplicate PAYMENT_BOUNCE_BACK prevented: externalReference={}",
          externalReference,
          new Exception("Duplicate caller stacktrace"));
      return existing.get();
    }

    ensureUnattributedPaymentRecorded(amount, externalReference);

    LedgerAccount unreconciledAccount = accounts.getUnreconciledBankReceiptsAccount();
    LedgerAccount incomingPaymentsAccount = accounts.getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), PAYMENT_BOUNCE_BACK.name());

    return ledgerTransactionService.createTransaction(
        PAYMENT_BOUNCE_BACK,
        Instant.now(clock),
        externalReference,
        metadata,
        accounts.entry(unreconciledAccount, amount),
        accounts.entry(incomingPaymentsAccount, amount.negate()));
  }

  void ensureUnattributedPaymentRecorded(BigDecimal amount, UUID externalReference) {
    boolean alreadyRecorded =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, UNATTRIBUTED_PAYMENT);
    if (!alreadyRecorded) {
      recordUnattributedPayment(amount, externalReference);
    }
  }
}
