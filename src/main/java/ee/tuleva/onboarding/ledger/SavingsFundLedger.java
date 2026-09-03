package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.*;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Ledger service for Tuleva savings fund transactions.
 *
 * <h2>Subscription Flow (buying fund units)</h2>
 *
 * <pre>
 * 1. recordPaymentReceived         INCOMING_PAYMENTS_CLEARING → User:CASH
 * 2. reservePaymentForSubscription User:CASH → User:CASH_RESERVED
 * 3. issueFundUnitsFromReserved    User:CASH_RESERVED → User:SUBSCRIPTIONS
 *                                  FUND_UNITS_OUTSTANDING → User:FUND_UNITS
 * 4. transferToFundAccount         INCOMING_PAYMENTS_CLEARING → FUND_INVESTMENT_CASH_CLEARING
 * </pre>
 *
 * <h2>Subscription Cancellation Flow (before fund units issued)</h2>
 *
 * <pre>
 * 1. reservePaymentForCancellation User:CASH → User:CASH_RESERVED
 * 2. recordPaymentCancelled        User:CASH_RESERVED → INCOMING_PAYMENTS_CLEARING
 * </pre>
 *
 * <h2>Redemption Flow (selling fund units)</h2>
 *
 * <pre>
 * 1. reserveFundUnitsForRedemption User:FUND_UNITS → User:FUND_UNITS_RESERVED
 * 2. redeemFundUnitsFromReserved   User:FUND_UNITS_RESERVED → FUND_UNITS_OUTSTANDING
 *                                  User:CASH_REDEMPTION → User:REDEMPTIONS
 * 3. transferFromFundAccount       FUND_INVESTMENT_CASH_CLEARING → PAYOUTS_CASH_CLEARING
 * 4. recordRedemptionPayout        PAYOUTS_CASH_CLEARING → User:CASH_REDEMPTION
 * </pre>
 *
 * <h2>Redemption Cancellation Flow (before payout)</h2>
 *
 * <pre>
 * 1. cancelRedemptionReservation   User:FUND_UNITS_RESERVED → User:FUND_UNITS
 * </pre>
 *
 * <h2>Unattributed Payment Flows</h2>
 *
 * <pre>
 * recordUnattributedPayment        INCOMING_PAYMENTS_CLEARING → UNRECONCILED_BANK_RECEIPTS
 * bounceBackUnattributedPayment    UNRECONCILED_BANK_RECEIPTS → INCOMING_PAYMENTS_CLEARING
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsFundLedger {

  private final SavingsFundLedgerAccounts accounts;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;
  private final RedemptionLedgerRecorder redemptionRecorder;
  private final UnattributedPaymentLedgerRecorder unattributedRecorder;

  @Getter
  @AllArgsConstructor
  public enum MetadataKey {
    OPERATION_TYPE("operationType"),
    PARTY_CODE("partyCode"),
    PARTY_TYPE("partyType"),
    EXTERNAL_REFERENCE("externalReference"),
    PAYER_IBAN("payerIban"),
    CUSTOMER_IBAN("customerIban"),
    NAV_PER_UNIT("navPerUnit"),
    REDEMPTION_REQUEST_ID("redemptionRequestId"),
    DESCRIPTION("description"),
    INSTRUMENT("instrument"),
    TICKER("ticker"),
    DISPLAY_NAME("displayName"),
    COUNTERPARTY_NAME("counterpartyName"),
    COUNTERPARTY_IBAN("counterpartyIban"),
    SUB_FAMILY_CODE("subFamilyCode");

    private final String key;
  }

  @Transactional
  public LedgerTransaction recordPaymentReceived(
      PartyRef party, BigDecimal amount, UUID externalReference) {
    return recordPaymentReceived(party, amount, externalReference, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction recordPaymentReceived(
      PartyRef party, BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    LedgerParty ledgerParty = accounts.getParty(party);
    LedgerAccount userCashAccount = accounts.getUserCashAccount(ledgerParty);
    LedgerAccount incomingPaymentsAccount = accounts.getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = accounts.partyMetadata(party, PAYMENT_RECEIVED);

    return ledgerTransactionService.createTransaction(
        PAYMENT_RECEIVED,
        accounts.transactionDate(bookingDate),
        externalReference,
        metadata,
        accounts.entry(incomingPaymentsAccount, amount),
        accounts.entry(userCashAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction reservePaymentForCancellation(
      PartyRef party, BigDecimal amount, UUID externalReference) {
    LedgerParty ledgerParty = accounts.getParty(party);
    LedgerAccount userCashAccount = accounts.getUserCashAccount(ledgerParty);
    LedgerAccount userCashReservedAccount = accounts.getUserCashReservedAccount(ledgerParty);

    Map<String, Object> metadata = accounts.partyMetadata(party, PAYMENT_CANCEL_REQUESTED);

    return ledgerTransactionService.createTransaction(
        PAYMENT_CANCEL_REQUESTED,
        Instant.now(clock),
        externalReference,
        metadata,
        accounts.entry(userCashAccount, amount),
        accounts.entry(userCashReservedAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordPaymentCancelled(
      PartyRef party, BigDecimal amount, UUID externalReference) {
    boolean unattributedPaymentExists =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, UNATTRIBUTED_PAYMENT);
    boolean reconciledToParty =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, UNATTRIBUTED_PAYMENT_RECONCILED);

    if (unattributedPaymentExists && !reconciledToParty) {
      return unattributedRecorder.bounceBackUnattributedPayment(amount, externalReference);
    }

    var existing =
        ledgerTransactionService.findByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_CANCELLED);
    if (existing.isPresent()) {
      log.error(
          "Duplicate PAYMENT_CANCELLED prevented: externalReference={}",
          externalReference,
          new Exception("Duplicate caller stacktrace"));
      return existing.get();
    }

    ensurePaymentReceivedExists(party, amount, externalReference);
    ensureReservationExists(party, amount, externalReference);

    LedgerParty ledgerParty = accounts.getParty(party);
    LedgerAccount userCashReservedAccount = accounts.getUserCashReservedAccount(ledgerParty);
    LedgerAccount incomingPaymentsAccount = accounts.getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = accounts.partyMetadata(party, PAYMENT_CANCELLED);

    return ledgerTransactionService.createTransaction(
        PAYMENT_CANCELLED,
        Instant.now(clock),
        externalReference,
        metadata,
        accounts.entry(userCashReservedAccount, amount),
        accounts.entry(incomingPaymentsAccount, amount.negate()));
  }

  private void ensurePaymentReceivedExists(
      PartyRef party, BigDecimal amount, UUID externalReference) {
    boolean alreadyRecorded =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_RECEIVED);
    if (!alreadyRecorded) {
      recordPaymentReceived(party, amount, externalReference);
    }
  }

  private void ensureReservationExists(PartyRef party, BigDecimal amount, UUID externalReference) {
    boolean reservationAlreadyExists =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_CANCEL_REQUESTED);
    if (!reservationAlreadyExists) {
      reservePaymentForCancellation(party, amount, externalReference);
    }
  }

  @Transactional
  public LedgerTransaction recordUnattributedPayment(BigDecimal amount, UUID externalReference) {
    return unattributedRecorder.recordUnattributedPayment(amount, externalReference);
  }

  @Transactional
  public LedgerTransaction recordUnattributedPayment(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    return unattributedRecorder.recordUnattributedPayment(amount, externalReference, bookingDate);
  }

  @Transactional
  public LedgerTransaction reservePaymentForSubscription(
      PartyRef party, BigDecimal amount, UUID externalReference) {
    LedgerParty ledgerParty = accounts.getParty(party);
    LedgerAccount userCashAccount = accounts.getUserCashAccount(ledgerParty);
    LedgerAccount userCashReservedAccount = accounts.getUserCashReservedAccount(ledgerParty);

    Map<String, Object> metadata = accounts.partyMetadata(party, PAYMENT_RESERVED);

    return ledgerTransactionService.createTransaction(
        PAYMENT_RESERVED,
        Instant.now(clock),
        externalReference,
        metadata,
        accounts.entry(userCashAccount, amount),
        accounts.entry(userCashReservedAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction issueFundUnitsFromReserved(
      PartyRef party,
      BigDecimal cashAmount,
      BigDecimal fundUnits,
      BigDecimal navPerUnit,
      UUID externalReference) {
    LedgerParty ledgerParty = accounts.getParty(party);
    LedgerAccount userCashReservedAccount = accounts.getUserCashReservedAccount(ledgerParty);
    LedgerAccount userUnitsAccount = accounts.getUserUnitsAccount(ledgerParty);
    LedgerAccount userSubscriptionsAccount = accounts.getUserSubscriptionsAccount(ledgerParty);
    LedgerAccount unitsOutstandingAccount = accounts.getFundUnitsOutstandingAccount();

    var metadata = new HashMap<>(accounts.partyMetadata(party, FUND_SUBSCRIPTION));
    metadata.put(NAV_PER_UNIT.getKey(), navPerUnit);

    return ledgerTransactionService.createTransaction(
        FUND_SUBSCRIPTION,
        Instant.now(clock),
        externalReference,
        metadata,
        accounts.entry(userCashReservedAccount, cashAmount),
        accounts.entry(userSubscriptionsAccount, cashAmount.negate()),
        accounts.entry(userUnitsAccount, fundUnits.negate()),
        accounts.entry(unitsOutstandingAccount, fundUnits));
  }

  @Transactional
  public LedgerTransaction transferToFundAccount(BigDecimal amount, UUID externalReference) {
    return transferToFundAccount(amount, externalReference, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction transferToFundAccount(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    LedgerAccount incomingPaymentsAccount = accounts.getIncomingPaymentsClearingAccount();
    LedgerAccount fundCashAccount = accounts.getFundInvestmentCashClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), FUND_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        FUND_TRANSFER,
        accounts.transactionDate(bookingDate),
        externalReference,
        metadata,
        accounts.entry(incomingPaymentsAccount, amount.negate()),
        accounts.entry(fundCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction bounceBackUnattributedPayment(
      BigDecimal amount, UUID externalReference) {
    return unattributedRecorder.bounceBackUnattributedPayment(amount, externalReference);
  }

  @Transactional
  public LedgerTransaction reconcileUnattributedPayment(
      PartyRef party, BigDecimal amount, UUID externalReference) {
    return reconcileUnattributedPayment(party, amount, externalReference, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction reconcileUnattributedPayment(
      PartyRef party, BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    var existing =
        ledgerTransactionService.findByExternalReferenceAndTransactionType(
            externalReference, UNATTRIBUTED_PAYMENT_RECONCILED);
    if (existing.isPresent()) {
      log.error(
          "Duplicate UNATTRIBUTED_PAYMENT_RECONCILED prevented: externalReference={}",
          externalReference,
          new Exception("Duplicate caller stacktrace"));
      return existing.get();
    }

    unattributedRecorder.ensureUnattributedPaymentRecorded(amount, externalReference);

    LedgerAccount unreconciledAccount = accounts.getUnreconciledBankReceiptsAccount();
    LedgerAccount incomingPaymentsAccount = accounts.getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = accounts.partyMetadata(party, UNATTRIBUTED_PAYMENT_RECONCILED);

    var reconciliation =
        ledgerTransactionService.createTransaction(
            UNATTRIBUTED_PAYMENT_RECONCILED,
            accounts.transactionDate(bookingDate),
            externalReference,
            metadata,
            accounts.entry(unreconciledAccount, amount),
            accounts.entry(incomingPaymentsAccount, amount.negate()));

    recordPaymentReceived(party, amount, externalReference, bookingDate);

    return reconciliation;
  }

  @Transactional
  public LedgerTransaction reserveFundUnitsForRedemption(
      PartyRef party, BigDecimal fundUnits, UUID externalReference) {
    return redemptionRecorder.reserveFundUnitsForRedemption(party, fundUnits, externalReference);
  }

  @Transactional
  public LedgerTransaction cancelRedemptionReservation(
      PartyRef party, BigDecimal fundUnits, UUID externalReference) {
    return redemptionRecorder.cancelRedemptionReservation(party, fundUnits, externalReference);
  }

  @Transactional
  public LedgerTransaction redeemFundUnitsFromReserved(
      PartyRef party,
      BigDecimal fundUnits,
      BigDecimal cashAmount,
      BigDecimal navPerUnit,
      UUID redemptionRequestId) {
    return redemptionRecorder.redeemFundUnitsFromReserved(
        party, fundUnits, cashAmount, navPerUnit, redemptionRequestId);
  }

  @Transactional
  public LedgerTransaction transferFromFundAccount(BigDecimal amount, UUID externalReference) {
    return redemptionRecorder.transferFromFundAccount(amount, externalReference);
  }

  @Transactional
  public LedgerTransaction transferFromFundAccount(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    return redemptionRecorder.transferFromFundAccount(amount, externalReference, bookingDate);
  }

  @Transactional
  public LedgerTransaction recordRedemptionPayout(
      PartyRef party, BigDecimal amount, String customerIban, UUID redemptionRequestId) {
    return redemptionRecorder.recordRedemptionPayout(
        party, amount, customerIban, redemptionRequestId);
  }

  @Transactional
  public LedgerTransaction recordRedemptionPayout(
      PartyRef party,
      BigDecimal amount,
      String customerIban,
      UUID redemptionRequestId,
      LocalDate bookingDate) {
    return redemptionRecorder.recordRedemptionPayout(
        party, amount, customerIban, redemptionRequestId, bookingDate);
  }

  @Transactional
  public LedgerTransaction recordAdjustment(
      String debitAccountName,
      @Nullable PartyRef debitParty,
      String creditAccountName,
      @Nullable PartyRef creditParty,
      BigDecimal amount,
      @Nullable UUID externalReference,
      String description) {
    if (debitParty != null && creditParty != null && !debitParty.equals(creditParty)) {
      throw new IllegalArgumentException(
          "Both accounts must belong to the same party or at least one must be a system account");
    }

    LedgerAccount debitAccount =
        debitParty != null
            ? accounts.resolvePartyAccount(debitParty, UserAccount.valueOf(debitAccountName))
            : accounts.resolveSystemAccount(debitAccountName);

    LedgerAccount creditAccount =
        creditParty != null
            ? accounts.resolvePartyAccount(creditParty, UserAccount.valueOf(creditAccountName))
            : accounts.resolveSystemAccount(creditAccountName);

    var metadataBuilder = new HashMap<String, Object>();
    metadataBuilder.put(OPERATION_TYPE.getKey(), ADJUSTMENT.name());
    if (description != null) {
      metadataBuilder.put(DESCRIPTION.getKey(), description);
    }

    return ledgerTransactionService.createTransaction(
        ADJUSTMENT,
        Instant.now(clock),
        externalReference,
        metadataBuilder,
        accounts.entry(debitAccount, amount),
        accounts.entry(creditAccount, amount.negate()));
  }

  public boolean hasLedgerEntry(UUID externalReference, TransactionType transactionType) {
    return ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, transactionType);
  }

  public boolean hasPricingEntry(UUID redemptionRequestId) {
    return hasLedgerEntry(redemptionRequestId, REDEMPTION_REQUEST);
  }

  public boolean hasPayoutEntry(UUID redemptionRequestId) {
    return hasLedgerEntry(redemptionRequestId, REDEMPTION_PAYOUT);
  }
}
