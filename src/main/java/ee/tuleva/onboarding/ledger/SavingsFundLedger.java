package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.SavingsFundTransactionType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.SavingsFundTransactionType.REDEMPTION_REQUEST;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.SUBSCRIPTIONS;

import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.user.User;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundLedger {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final LedgerAccountRepository ledgerAccountRepository;
  private final Clock clock;

  @Getter
  @AllArgsConstructor
  public enum SystemAccount {
    INCOMING_PAYMENTS_CLEARING(ASSET, EUR),
    UNRECONCILED_BANK_RECEIPTS(ASSET, EUR),
    FUND_INVESTMENT_CASH_CLEARING(ASSET, EUR),
    FUND_UNITS_OUTSTANDING(LIABILITY, FUND_UNIT),
    PAYOUTS_CASH_CLEARING(ASSET, EUR);

    private final AccountType accountType;
    private final AssetType assetType;
  }

  @Getter
  @AllArgsConstructor
  public enum UserAccount {
    CASH(LIABILITY, EUR),
    CASH_RESERVED(LIABILITY, EUR),
    CASH_REDEMPTION(LIABILITY, EUR),
    FUND_UNITS(LIABILITY, FUND_UNIT),
    FUND_UNITS_RESERVED(LIABILITY, FUND_UNIT),
    SUBSCRIPTIONS(INCOME, EUR),
    REDEMPTIONS(EXPENSE, EUR);

    private final AccountType accountType;
    private final AssetType assetType;
  }

  public enum SavingsFundTransactionType {
    PAYMENT_RECEIVED,
    UNATTRIBUTED_PAYMENT,
    PAYMENT_BOUNCE_BACK,
    PAYMENT_RESERVED,
    FUND_SUBSCRIPTION,
    FUND_TRANSFER,
    LATE_ATTRIBUTION,
    REDEMPTION_RESERVED,
    REDEMPTION_REQUEST,
    FUND_CASH_TRANSFER,
    REDEMPTION_PAYOUT
  }

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
      User user, BigDecimal amount, String externalReference) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashAccount = getUserCashAccount(userParty);
    LedgerAccount incomingPaymentsAccount = getSystemAccount(INCOMING_PAYMENTS_CLEARING);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, PAYMENT_RECEIVED.name(),
            USER_ID.key, user.getId(),
            PERSONAL_CODE.key, user.getPersonalCode(),
            EXTERNAL_REFERENCE.key, externalReference);

    return ledgerTransactionService.createTransaction(
        Instant.now(clock),
        metadata,
        entry(incomingPaymentsAccount, amount),
        entry(userCashAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordUnattributedPayment(
      BigDecimal amount, String payerIban, String externalReference) {
    LedgerAccount unreconciledAccount = getSystemAccount(UNRECONCILED_BANK_RECEIPTS);
    LedgerAccount incomingPaymentsAccount = getSystemAccount(INCOMING_PAYMENTS_CLEARING);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, UNATTRIBUTED_PAYMENT.name(),
            PAYER_IBAN.key, payerIban,
            EXTERNAL_REFERENCE.key, externalReference);

    return ledgerTransactionService.createTransaction(
        Instant.now(clock),
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
    LedgerAccount unitsOutstandingAccount = getSystemAccount(FUND_UNITS_OUTSTANDING);

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
    LedgerAccount incomingPaymentsAccount = getSystemAccount(INCOMING_PAYMENTS_CLEARING);
    LedgerAccount fundCashAccount = getSystemAccount(FUND_INVESTMENT_CASH_CLEARING);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, FUND_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        Instant.now(clock),
        metadata,
        entry(incomingPaymentsAccount, amount.negate()),
        entry(fundCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction bounceBackUnattributedPayment(BigDecimal amount, String payerIban) {
    LedgerAccount unreconciledAccount = getSystemAccount(UNRECONCILED_BANK_RECEIPTS);
    LedgerAccount incomingPaymentsAccount = getSystemAccount(INCOMING_PAYMENTS_CLEARING);

    Map<String, Object> metadata =
        Map.of(OPERATION_TYPE.key, PAYMENT_BOUNCE_BACK.name(), PAYER_IBAN.key, payerIban);

    return ledgerTransactionService.createTransaction(
        Instant.now(clock),
        metadata,
        entry(unreconciledAccount, amount),
        entry(incomingPaymentsAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction attributeLatePayment(User user, BigDecimal amount) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashAccount = getUserCashAccount(userParty);
    LedgerAccount unreconciledAccount = getSystemAccount(UNRECONCILED_BANK_RECEIPTS);

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
  public LedgerTransaction processRedemptionFromReserved(
      User user, BigDecimal fundUnits, BigDecimal cashAmount, BigDecimal navPerUnit) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userUnitsReservedAccount = getUserUnitsReservedAccount(userParty);
    LedgerAccount userCashRedemptionAccount = getUserCashRedemptionAccount(userParty);
    LedgerAccount unitsOutstandingAccount = getSystemAccount(FUND_UNITS_OUTSTANDING);
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
  public LedgerTransaction transferFundToPayoutCash(BigDecimal amount) {
    LedgerAccount fundCashAccount = getSystemAccount(FUND_INVESTMENT_CASH_CLEARING);
    LedgerAccount payoutsCashAccount = getSystemAccount(PAYOUTS_CASH_CLEARING);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, FUND_CASH_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        Instant.now(clock),
        metadata,
        entry(fundCashAccount, amount.negate()),
        entry(payoutsCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction processRedemptionPayoutFromCashRedemption(
      User user, BigDecimal amount, String customerIban) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashRedemptionAccount = getUserCashRedemptionAccount(userParty);
    LedgerAccount payoutsCashAccount = getSystemAccount(PAYOUTS_CASH_CLEARING);

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
    return ledgerPartyService
        .getParty(user)
        .orElseThrow(
            () -> new IllegalStateException("User not onboarded: " + user.getPersonalCode()));
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
}
