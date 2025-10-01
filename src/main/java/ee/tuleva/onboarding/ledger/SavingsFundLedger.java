package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.SavingsFundTransactionType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.SavingsFundTransactionType.REDEMPTION_REQUEST;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.*;

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

  public enum SystemAccount {
    INCOMING_PAYMENTS_CLEARING,
    UNRECONCILED_BANK_RECEIPTS,
    SUBSCRIPTIONS,
    FUND_INVESTMENT_CASH_CLEARING,
    FUND_UNITS_OUTSTANDING,
    REDEMPTIONS,
    PAYOUTS_CASH_CLEARING
  }

  public enum UserAccount {
    CASH,
    CASH_RESERVED,
    CASH_REDEMPTION,
    FUND_UNITS,
    FUND_UNITS_RESERVED
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
    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET);

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
    LedgerAccount unreconciledAccount = getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET);
    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET);

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
    LedgerAccount subscriptionsIncomeAccount = getSystemAccount(SUBSCRIPTIONS, EUR, INCOME);
    LedgerAccount unitsOutstandingAccount =
        getSystemAccount(FUND_UNITS_OUTSTANDING, FUND_UNIT, LIABILITY);

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
        entry(subscriptionsIncomeAccount, cashAmount.negate()),
        entry(userUnitsAccount, fundUnits.negate()),
        entry(unitsOutstandingAccount, fundUnits));
  }

  @Transactional
  public LedgerTransaction transferToFundAccount(BigDecimal amount) {
    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET);
    LedgerAccount fundCashAccount = getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, FUND_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        Instant.now(clock),
        metadata,
        entry(incomingPaymentsAccount, amount.negate()),
        entry(fundCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction bounceBackUnattributedPayment(BigDecimal amount, String payerIban) {
    LedgerAccount unreconciledAccount = getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET);
    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET);

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
    LedgerAccount unreconciledAccount = getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET);

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
    LedgerAccount unitsOutstandingAccount =
        getSystemAccount(FUND_UNITS_OUTSTANDING, FUND_UNIT, LIABILITY);
    LedgerAccount redemptionExpenseAccount = getSystemAccount(REDEMPTIONS, EUR, EXPENSE);

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
        entry(redemptionExpenseAccount, cashAmount));
  }

  @Transactional
  public LedgerTransaction transferFundToPayoutCash(BigDecimal amount) {
    LedgerAccount fundCashAccount = getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET);
    LedgerAccount payoutsCashAccount = getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET);

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
    LedgerAccount payoutsCashAccount = getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET);

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

  private LedgerAccount getUserCashAccount(LedgerParty owner) {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            owner, CASH.name(), USER_ACCOUNT, EUR, LIABILITY)
        .orElseGet(() -> ledgerAccountService.createUserAccount(owner, CASH, LIABILITY, EUR));
  }

  private LedgerAccount getUserCashReservedAccount(LedgerParty owner) {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            owner, CASH_RESERVED.name(), USER_ACCOUNT, EUR, LIABILITY)
        .orElseGet(
            () -> ledgerAccountService.createUserAccount(owner, CASH_RESERVED, LIABILITY, EUR));
  }

  private LedgerAccount getUserCashRedemptionAccount(LedgerParty owner) {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            owner, CASH_REDEMPTION.name(), USER_ACCOUNT, EUR, LIABILITY)
        .orElseGet(
            () -> ledgerAccountService.createUserAccount(owner, CASH_REDEMPTION, LIABILITY, EUR));
  }

  private LedgerAccount getUserUnitsAccount(LedgerParty userParty) {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            userParty, FUND_UNITS.name(), USER_ACCOUNT, FUND_UNIT, LIABILITY)
        .orElseGet(
            () ->
                ledgerAccountService.createUserAccount(
                    userParty, FUND_UNITS, LIABILITY, FUND_UNIT));
  }

  private LedgerAccount getUserUnitsReservedAccount(LedgerParty userParty) {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            userParty, FUND_UNITS_RESERVED.name(), USER_ACCOUNT, FUND_UNIT, LIABILITY)
        .orElseGet(
            () ->
                ledgerAccountService.createUserAccount(
                    userParty, FUND_UNITS_RESERVED, LIABILITY, FUND_UNIT));
  }

  private LedgerAccount getSystemAccount(
      SystemAccount systemAccount, AssetType assetType, AccountType accountType) {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            null, systemAccount.name(), SYSTEM_ACCOUNT, assetType, accountType)
        .orElseGet(
            () ->
                ledgerAccountService.createSystemAccount(
                    systemAccount.name(), assetType, accountType));
  }
}
