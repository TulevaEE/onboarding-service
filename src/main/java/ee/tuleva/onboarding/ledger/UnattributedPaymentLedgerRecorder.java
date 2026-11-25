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
    return recordUnattributedPayment(
        amount,
        externalReference,
        bookingDate,
        accounts.getIncomingPaymentsClearingAccount(),
        accounts.getUnreconciledBankReceiptsAccount());
  }

  @Transactional
  LedgerTransaction recordUnattributedPayoutAccountReceipt(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    return recordUnattributedPayment(
        amount,
        externalReference,
        bookingDate,
        accounts.getPayoutsCashClearingAccount(),
        accounts.getUnreconciledPayoutAccountReceiptsAccount());
  }

  private LedgerTransaction recordUnattributedPayment(
      BigDecimal amount,
      UUID externalReference,
      LocalDate bookingDate,
      LedgerAccount receivingCashAccount,
      LedgerAccount unreconciledAccount) {

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), UNATTRIBUTED_PAYMENT.name());

    return ledgerTransactionService.createTransaction(
        UNATTRIBUTED_PAYMENT,
        accounts.transactionDate(bookingDate),
        externalReference,
        metadata,
        accounts.entry(receivingCashAccount, amount),
        accounts.entry(unreconciledAccount, amount.negate()));
  }

  @Transactional
  LedgerTransaction bounceBackUnattributedPayment(BigDecimal amount, UUID externalReference) {
    return bounceBackUnattributedPayment(
        amount,
        externalReference,
        LocalDate.now(clock),
        Instant.now(clock),
        accounts.getIncomingPaymentsClearingAccount(),
        accounts.getUnreconciledBankReceiptsAccount());
  }

  @Transactional
  LedgerTransaction bounceBackUnattributedPayoutAccountReceipt(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    return bounceBackUnattributedPayment(
        amount,
        externalReference,
        bookingDate,
        accounts.transactionDate(bookingDate),
        accounts.getPayoutsCashClearingAccount(),
        accounts.getUnreconciledPayoutAccountReceiptsAccount());
  }

  private LedgerTransaction bounceBackUnattributedPayment(
      BigDecimal amount,
      UUID externalReference,
      LocalDate bookingDate,
      Instant transactionDate,
      LedgerAccount receivingCashAccount,
      LedgerAccount unreconciledAccount) {
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

    ensureUnattributedPaymentRecorded(
        amount, externalReference, bookingDate, receivingCashAccount, unreconciledAccount);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), PAYMENT_BOUNCE_BACK.name());

    return ledgerTransactionService.createTransaction(
        PAYMENT_BOUNCE_BACK,
        transactionDate,
        externalReference,
        metadata,
        accounts.entry(unreconciledAccount, amount),
        accounts.entry(receivingCashAccount, amount.negate()));
  }

  void ensureUnattributedPaymentRecorded(BigDecimal amount, UUID externalReference) {
    ensureUnattributedPaymentRecorded(
        amount,
        externalReference,
        LocalDate.now(clock),
        accounts.getIncomingPaymentsClearingAccount(),
        accounts.getUnreconciledBankReceiptsAccount());
  }

  private void ensureUnattributedPaymentRecorded(
      BigDecimal amount,
      UUID externalReference,
      LocalDate bookingDate,
      LedgerAccount receivingCashAccount,
      LedgerAccount unreconciledAccount) {
    boolean alreadyRecorded =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, UNATTRIBUTED_PAYMENT);
    if (!alreadyRecorded) {
      recordUnattributedPayment(
          amount, externalReference, bookingDate, receivingCashAccount, unreconciledAccount);
    }
  }
}
