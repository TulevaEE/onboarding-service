package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.user.User;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * attributeLatePayment             UNRECONCILED_BANK_RECEIPTS → User:CASH
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsFundLedger {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  @Getter
  @AllArgsConstructor
  public enum MetadataKey {
    OPERATION_TYPE("operationType"),
    USER_ID("userId"),
    PERSONAL_CODE("personalCode"),
    EXTERNAL_REFERENCE("externalReference"),
    PAYER_IBAN("payerIban"),
    CUSTOMER_IBAN("customerIban"),
    NAV_PER_UNIT("navPerUnit"),
    REDEMPTION_REQUEST_ID("redemptionRequestId"),
    DESCRIPTION("description"),
    INSTRUMENT("instrument"),
    TICKER("ticker"),
    DISPLAY_NAME("displayName");

    private final String key;
  }

  @Transactional
  public LedgerTransaction recordPaymentReceived(
      User user, BigDecimal amount, UUID externalReference) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashAccount = getUserCashAccount(userParty);
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, PAYMENT_RECEIVED.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode());

    return ledgerTransactionService.createTransaction(
        PAYMENT_RECEIVED,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(incomingPaymentsAccount, amount),
        entry(userCashAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction reservePaymentForCancellation(
      User user, BigDecimal amount, UUID externalReference) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashAccount = getUserCashAccount(userParty);
    LedgerAccount userCashReservedAccount = getUserCashReservedAccount(userParty);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, PAYMENT_CANCEL_REQUESTED.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode());

    return ledgerTransactionService.createTransaction(
        PAYMENT_CANCEL_REQUESTED,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(userCashAccount, amount),
        entry(userCashReservedAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordPaymentCancelled(
      User user, BigDecimal amount, UUID externalReference) {
    boolean unattributedPaymentExists =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, UNATTRIBUTED_PAYMENT);

    if (unattributedPaymentExists) {
      return bounceBackUnattributedPayment(amount, externalReference);
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

    ensurePaymentReceivedExists(user, amount, externalReference);
    ensureReservationExists(user, amount, externalReference);

    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashReservedAccount = getUserCashReservedAccount(userParty);
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, PAYMENT_CANCELLED.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode());

    return ledgerTransactionService.createTransaction(
        PAYMENT_CANCELLED,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(userCashReservedAccount, amount),
        entry(incomingPaymentsAccount, amount.negate()));
  }

  private void ensurePaymentReceivedExists(User user, BigDecimal amount, UUID externalReference) {
    boolean alreadyRecorded =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_RECEIVED);
    if (!alreadyRecorded) {
      recordPaymentReceived(user, amount, externalReference);
    }
  }

  private void ensureReservationExists(User user, BigDecimal amount, UUID externalReference) {
    boolean reservationAlreadyExists =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_CANCEL_REQUESTED);
    if (!reservationAlreadyExists) {
      reservePaymentForCancellation(user, amount, externalReference);
    }
  }

  @Transactional
  public LedgerTransaction recordUnattributedPayment(BigDecimal amount, UUID externalReference) {
    LedgerAccount unreconciledAccount = getUnreconciledBankReceiptsAccount();
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, UNATTRIBUTED_PAYMENT.name());

    return ledgerTransactionService.createTransaction(
        UNATTRIBUTED_PAYMENT,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(incomingPaymentsAccount, amount),
        entry(unreconciledAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction reservePaymentForSubscription(User user, BigDecimal amount) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashAccount = getUserCashAccount(userParty);
    LedgerAccount userCashReservedAccount = getUserCashReservedAccount(userParty);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, PAYMENT_RESERVED.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode());

    return ledgerTransactionService.createTransaction(
        PAYMENT_RESERVED,
        Instant.now(clock),
        metadata,
        entry(userCashAccount, amount),
        entry(userCashReservedAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction issueFundUnitsFromReserved(
      User user, BigDecimal cashAmount, BigDecimal fundUnits, BigDecimal navPerUnit) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashReservedAccount = getUserCashReservedAccount(userParty);
    LedgerAccount userUnitsAccount = getUserUnitsAccount(userParty);
    LedgerAccount userSubscriptionsAccount = getUserSubscriptionsAccount(userParty);
    LedgerAccount unitsOutstandingAccount = getFundUnitsOutstandingAccount();

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, FUND_SUBSCRIPTION.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode(),
            NAV_PER_UNIT.key, navPerUnit);

    return ledgerTransactionService.createTransaction(
        FUND_SUBSCRIPTION,
        Instant.now(clock),
        metadata,
        entry(userCashReservedAccount, cashAmount),
        entry(userSubscriptionsAccount, cashAmount.negate()),
        entry(userUnitsAccount, fundUnits.negate()),
        entry(unitsOutstandingAccount, fundUnits));
  }

  @Transactional
  public LedgerTransaction transferToFundAccount(BigDecimal amount) {
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();
    LedgerAccount fundCashAccount = getFundInvestmentCashClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, FUND_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        FUND_TRANSFER,
        Instant.now(clock),
        metadata,
        entry(incomingPaymentsAccount, amount.negate()),
        entry(fundCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction bounceBackUnattributedPayment(
      BigDecimal amount, UUID externalReference) {
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

    LedgerAccount unreconciledAccount = getUnreconciledBankReceiptsAccount();
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, PAYMENT_BOUNCE_BACK.name());

    return ledgerTransactionService.createTransaction(
        PAYMENT_BOUNCE_BACK,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(unreconciledAccount, amount),
        entry(incomingPaymentsAccount, amount.negate()));
  }

  private void ensureUnattributedPaymentRecorded(BigDecimal amount, UUID externalReference) {
    boolean alreadyRecorded =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, UNATTRIBUTED_PAYMENT);
    if (!alreadyRecorded) {
      recordUnattributedPayment(amount, externalReference);
    }
  }

  @Transactional
  public LedgerTransaction attributeLatePayment(User user, BigDecimal amount) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashAccount = getUserCashAccount(userParty);
    LedgerAccount unreconciledAccount = getUnreconciledBankReceiptsAccount();

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, LATE_ATTRIBUTION.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode());

    return ledgerTransactionService.createTransaction(
        LATE_ATTRIBUTION,
        Instant.now(clock),
        metadata,
        entry(unreconciledAccount, amount),
        entry(userCashAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction reserveFundUnitsForRedemption(User user, BigDecimal fundUnits) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userUnitsAccount = getUserUnitsAccount(userParty);
    LedgerAccount userUnitsReservedAccount = getUserUnitsReservedAccount(userParty);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, REDEMPTION_RESERVED.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode());

    return ledgerTransactionService.createTransaction(
        REDEMPTION_RESERVED,
        Instant.now(clock),
        metadata,
        entry(userUnitsAccount, fundUnits),
        entry(userUnitsReservedAccount, fundUnits.negate()));
  }

  @Transactional
  public LedgerTransaction cancelRedemptionReservation(User user, BigDecimal fundUnits) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userUnitsAccount = getUserUnitsAccount(userParty);
    LedgerAccount userUnitsReservedAccount = getUserUnitsReservedAccount(userParty);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, REDEMPTION_CANCELLED.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode());

    return ledgerTransactionService.createTransaction(
        REDEMPTION_CANCELLED,
        Instant.now(clock),
        metadata,
        entry(userUnitsReservedAccount, fundUnits),
        entry(userUnitsAccount, fundUnits.negate()));
  }

  @Transactional
  public LedgerTransaction redeemFundUnitsFromReserved(
      User user, BigDecimal fundUnits, BigDecimal cashAmount, BigDecimal navPerUnit) {
    return redeemFundUnitsFromReserved(user, fundUnits, cashAmount, navPerUnit, null);
  }

  @Transactional
  public LedgerTransaction redeemFundUnitsFromReserved(
      User user,
      BigDecimal fundUnits,
      BigDecimal cashAmount,
      BigDecimal navPerUnit,
      UUID redemptionRequestId) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userUnitsReservedAccount = getUserUnitsReservedAccount(userParty);
    LedgerAccount userCashRedemptionAccount = getUserCashRedemptionAccount(userParty);
    LedgerAccount unitsOutstandingAccount = getFundUnitsOutstandingAccount();
    LedgerAccount userRedemptionsAccount = getUserRedemptionsAccount(userParty);

    var metadataBuilder = new HashMap<String, Object>();
    metadataBuilder.put(OPERATION_TYPE.key, REDEMPTION_REQUEST.name());
    metadataBuilder.put(USER_ID.key, user.getId());
    metadataBuilder.put(PERSONAL_CODE.key, user.getPersonalCode());
    metadataBuilder.put(NAV_PER_UNIT.key, navPerUnit);
    if (redemptionRequestId != null) {
      metadataBuilder.put(REDEMPTION_REQUEST_ID.key, redemptionRequestId);
    }

    return ledgerTransactionService.createTransaction(
        REDEMPTION_REQUEST,
        Instant.now(clock),
        redemptionRequestId,
        metadataBuilder,
        entry(userUnitsReservedAccount, fundUnits),
        entry(unitsOutstandingAccount, fundUnits.negate()),
        entry(userCashRedemptionAccount, cashAmount.negate()),
        entry(userRedemptionsAccount, cashAmount));
  }

  @Transactional
  public LedgerTransaction transferFromFundAccount(BigDecimal amount) {
    LedgerAccount fundCashAccount = getFundInvestmentCashClearingAccount();
    LedgerAccount payoutsCashAccount = getPayoutsCashClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, FUND_CASH_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        FUND_CASH_TRANSFER,
        Instant.now(clock),
        metadata,
        entry(fundCashAccount, amount.negate()),
        entry(payoutsCashAccount, amount));
  }

  // TODO: remove, only used in tests
  @Transactional
  public LedgerTransaction recordRedemptionPayout(
      User user, BigDecimal amount, String customerIban) {
    return recordRedemptionPayout(user, amount, customerIban, null);
  }

  @Transactional
  public LedgerTransaction recordRedemptionPayout(
      User user, BigDecimal amount, String customerIban, UUID redemptionRequestId) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashRedemptionAccount = getUserCashRedemptionAccount(userParty);
    LedgerAccount payoutsCashAccount = getPayoutsCashClearingAccount();

    var metadataBuilder = new HashMap<String, Object>();
    metadataBuilder.put(OPERATION_TYPE.key, REDEMPTION_PAYOUT.name());
    metadataBuilder.put(USER_ID.key, user.getId());
    metadataBuilder.put(PERSONAL_CODE.key, user.getPersonalCode());
    metadataBuilder.put(CUSTOMER_IBAN.key, customerIban);
    if (redemptionRequestId != null) {
      metadataBuilder.put(REDEMPTION_REQUEST_ID.key, redemptionRequestId);
    }

    return ledgerTransactionService.createTransaction(
        REDEMPTION_PAYOUT,
        Instant.now(clock),
        redemptionRequestId,
        metadataBuilder,
        entry(payoutsCashAccount, amount.negate()),
        entry(userCashRedemptionAccount, amount));
  }

  @Transactional
  public LedgerTransaction recordAdjustment(
      String debitAccountName,
      String debitPersonalCode,
      String creditAccountName,
      String creditPersonalCode,
      BigDecimal amount,
      UUID externalReference,
      String description) {
    boolean debitIsUser = debitPersonalCode != null;
    boolean creditIsUser = creditPersonalCode != null;

    if (debitIsUser && creditIsUser && !debitPersonalCode.equals(creditPersonalCode)) {
      throw new IllegalArgumentException(
          "Both accounts must belong to the same user or at least one must be a system account");
    }

    LedgerAccount debitAccount =
        debitIsUser
            ? resolveUserAccount(debitPersonalCode, UserAccount.valueOf(debitAccountName))
            : getSystemAccount(SystemAccount.valueOf(debitAccountName));

    LedgerAccount creditAccount =
        creditIsUser
            ? resolveUserAccount(creditPersonalCode, UserAccount.valueOf(creditAccountName))
            : getSystemAccount(SystemAccount.valueOf(creditAccountName));

    var metadataBuilder = new HashMap<String, Object>();
    metadataBuilder.put(OPERATION_TYPE.key, ADJUSTMENT.name());
    if (description != null) {
      metadataBuilder.put(DESCRIPTION.key, description);
    }

    return ledgerTransactionService.createTransaction(
        ADJUSTMENT,
        Instant.now(clock),
        externalReference,
        metadataBuilder,
        entry(debitAccount, amount),
        entry(creditAccount, amount.negate()));
  }

  private LedgerAccount resolveUserAccount(String personalCode, UserAccount userAccount) {
    LedgerParty party =
        ledgerPartyService
            .getParty(personalCode)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Ledger party not found: personalCode=" + personalCode));
    return getUserAccount(party, userAccount);
  }

  @Transactional
  public LedgerTransaction recordBankFee(
      BigDecimal amount, UUID externalReference, SystemAccount clearingAccount) {
    LedgerAccount bankFeeExpenseAccount = getSystemAccount(SystemAccount.BANK_FEE);
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, TransactionType.BANK_FEE.name());

    return ledgerTransactionService.createTransaction(
        TransactionType.BANK_FEE,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(bankFeeExpenseAccount, amount.negate()),
        entry(clearingLedgerAccount, amount));
  }

  @Transactional
  public LedgerTransaction recordInterestReceived(
      BigDecimal amount, UUID externalReference, SystemAccount clearingAccount) {
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount);
    LedgerAccount interestIncomeAccount = getSystemAccount(INTEREST_INCOME);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, INTEREST_RECEIVED.name());

    return ledgerTransactionService.createTransaction(
        INTEREST_RECEIVED,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(clearingLedgerAccount, amount),
        entry(interestIncomeAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordBankAdjustment(
      BigDecimal amount, UUID externalReference, SystemAccount clearingAccount) {
    LedgerAccount bankAdjustmentAccount = getSystemAccount(SystemAccount.BANK_ADJUSTMENT);
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount);

    Map<String, Object> metadata =
        Map.of(OPERATION_TYPE.key, TransactionType.BANK_ADJUSTMENT.name());

    return ledgerTransactionService.createTransaction(
        TransactionType.BANK_ADJUSTMENT,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(bankAdjustmentAccount, amount.negate()),
        entry(clearingLedgerAccount, amount));
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }

  private LedgerParty getUserParty(User user) {
    return ledgerPartyService.getParty(user).orElseGet(() -> ledgerPartyService.createParty(user));
  }

  private LedgerAccount getUserAccount(LedgerParty owner, UserAccount userAccount) {
    return ledgerAccountService
        .findUserAccount(owner, userAccount)
        .orElseGet(() -> ledgerAccountService.createUserAccount(owner, userAccount));
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerAccountService
        .findSystemAccount(systemAccount)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount));
  }

  private LedgerAccount getUserCashAccount(LedgerParty owner) {
    return getUserAccount(owner, CASH);
  }

  private LedgerAccount getUserCashReservedAccount(LedgerParty owner) {
    return getUserAccount(owner, CASH_RESERVED);
  }

  private LedgerAccount getUserCashRedemptionAccount(LedgerParty owner) {
    return getUserAccount(owner, CASH_REDEMPTION);
  }

  private LedgerAccount getUserUnitsAccount(LedgerParty owner) {
    return getUserAccount(owner, FUND_UNITS);
  }

  private LedgerAccount getUserUnitsReservedAccount(LedgerParty owner) {
    return getUserAccount(owner, FUND_UNITS_RESERVED);
  }

  private LedgerAccount getUserSubscriptionsAccount(LedgerParty owner) {
    return getUserAccount(owner, SUBSCRIPTIONS);
  }

  private LedgerAccount getUserRedemptionsAccount(LedgerParty owner) {
    return getUserAccount(owner, REDEMPTIONS);
  }

  private LedgerAccount getIncomingPaymentsClearingAccount() {
    return getSystemAccount(INCOMING_PAYMENTS_CLEARING);
  }

  private LedgerAccount getUnreconciledBankReceiptsAccount() {
    return getSystemAccount(UNRECONCILED_BANK_RECEIPTS);
  }

  private LedgerAccount getFundInvestmentCashClearingAccount() {
    return getSystemAccount(FUND_INVESTMENT_CASH_CLEARING);
  }

  private LedgerAccount getFundUnitsOutstandingAccount() {
    return getSystemAccount(FUND_UNITS_OUTSTANDING);
  }

  private LedgerAccount getPayoutsCashClearingAccount() {
    return getSystemAccount(PAYOUTS_CASH_CLEARING);
  }

  public boolean hasLedgerEntry(UUID externalReference) {
    return ledgerTransactionService.existsByExternalReference(externalReference);
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

  @Transactional
  public LedgerTransaction recordTradeSettlement(
      BigDecimal amount,
      UUID externalReference,
      SystemAccount clearingAccount,
      String isin,
      String ticker,
      String displayName) {
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount);
    LedgerAccount tradeSettlementAccount = getTradeSettlementAccount(isin);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, TRADE_SETTLEMENT.name(),
            INSTRUMENT.key, isin,
            TICKER.key, ticker,
            DISPLAY_NAME.key, displayName);

    return ledgerTransactionService.createTransaction(
        TRADE_SETTLEMENT,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(clearingLedgerAccount, amount),
        entry(tradeSettlementAccount, amount.negate()));
  }

  private LedgerAccount getTradeSettlementAccount(String isin) {
    String accountName = "TRADE_SETTLEMENT:" + isin;
    return ledgerAccountService
        .findSystemAccountByName(accountName, AccountType.ASSET, AssetType.EUR)
        .orElseGet(
            () ->
                ledgerAccountService.createSystemAccount(
                    accountName, AccountType.ASSET, AssetType.EUR));
  }
}
