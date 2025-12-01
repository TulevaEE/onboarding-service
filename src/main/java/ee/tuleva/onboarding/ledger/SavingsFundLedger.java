package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;
import static ee.tuleva.onboarding.ledger.SavingsFundTransactionType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundTransactionType.REDEMPTION_REQUEST;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.user.User;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Ledger service for Tuleva savings fund transactions.
 *
 * <h2>Subscription Flow (buying fund units)</h2>
 *
 * <pre>
 * 1. {@link #recordPaymentReceived}            - Record incoming payment from user
 * 2. {@link #reservePaymentForSubscription}    - Move cash from available to reserved
 * 3. {@link #issueFundUnitsFromReserved}       - Issue fund units, record subscription
 * 4. {@link #transferToFundAccount}            - Transfer cash to fund investment account
 * </pre>
 *
 * <h2>Redemption Flow (selling fund units)</h2>
 *
 * <pre>
 * 1. {@link #reserveFundUnitsForRedemption}    - Reserve user's fund units
 * 2. {@link #redeemFundUnitsFromReserved}      - Convert units to cash (pending payout)
 * 3. {@link #transferFromFundAccount}          - Transfer cash from fund to payout account
 * 4. {@link #recordRedemptionPayout}           - Pay out cash to user's bank account
 * </pre>
 *
 * <h2>Edge Cases</h2>
 *
 * <ul>
 *   <li>{@link #recordUnattributedPayment} - Payment cannot be matched to a user
 *   <li>{@link #bounceBackUnattributedPayment} - Return unattributed payment to sender
 *   <li>{@link #attributeLatePayment} - Attribute previously unattributed payment to a user
 * </ul>
 */
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
    NAV_PER_UNIT("navPerUnit");

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
        Instant.now(clock),
        externalReference,
        metadata,
        entry(incomingPaymentsAccount, amount),
        entry(userCashAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordUnattributedPayment(BigDecimal amount, UUID externalReference) {
    LedgerAccount unreconciledAccount = getUnreconciledBankReceiptsAccount();
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, UNATTRIBUTED_PAYMENT.name());

    return ledgerTransactionService.createTransaction(
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
        Instant.now(clock),
        metadata,
        entry(incomingPaymentsAccount, amount.negate()),
        entry(fundCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction bounceBackUnattributedPayment(
      BigDecimal amount, UUID externalReference) {
    LedgerAccount unreconciledAccount = getUnreconciledBankReceiptsAccount();
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, PAYMENT_BOUNCE_BACK.name());

    return ledgerTransactionService.createTransaction(
        Instant.now(clock),
        externalReference,
        metadata,
        entry(unreconciledAccount, amount),
        entry(incomingPaymentsAccount, amount.negate()));
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
        Instant.now(clock),
        metadata,
        entry(userUnitsAccount, fundUnits),
        entry(userUnitsReservedAccount, fundUnits.negate()));
  }

  @Transactional
  public LedgerTransaction redeemFundUnitsFromReserved(
      User user, BigDecimal fundUnits, BigDecimal cashAmount, BigDecimal navPerUnit) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userUnitsReservedAccount = getUserUnitsReservedAccount(userParty);
    LedgerAccount userCashRedemptionAccount = getUserCashRedemptionAccount(userParty);
    LedgerAccount unitsOutstandingAccount = getFundUnitsOutstandingAccount();
    LedgerAccount userRedemptionsAccount = getUserRedemptionsAccount(userParty);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, REDEMPTION_REQUEST.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode(),
            NAV_PER_UNIT.key, navPerUnit);

    return ledgerTransactionService.createTransaction(
        Instant.now(clock),
        metadata,
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
        Instant.now(clock),
        metadata,
        entry(fundCashAccount, amount.negate()),
        entry(payoutsCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction recordRedemptionPayout(
      User user, BigDecimal amount, String customerIban) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashRedemptionAccount = getUserCashRedemptionAccount(userParty);
    LedgerAccount payoutsCashAccount = getPayoutsCashClearingAccount();

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, REDEMPTION_PAYOUT.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode(),
            CUSTOMER_IBAN.key, customerIban);

    return ledgerTransactionService.createTransaction(
        Instant.now(clock),
        metadata,
        entry(payoutsCashAccount, amount.negate()),
        entry(userCashRedemptionAccount, amount));
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

  public boolean hasLedgerEntry(UUID paymentId) {
    return ledgerTransactionService.existsByExternalReference(paymentId);
  }
}
