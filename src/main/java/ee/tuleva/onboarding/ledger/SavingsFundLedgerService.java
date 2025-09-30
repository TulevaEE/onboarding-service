package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static ee.tuleva.onboarding.ledger.SavingsFundLedgerService.SystemAccount.*;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.user.User;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile({"dev", "test"})
@Service
@RequiredArgsConstructor
public class SavingsFundLedgerService {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final LedgerAccountRepository ledgerAccountRepository;

  public enum SystemAccount {
    INCOMING_PAYMENTS_CLEARING,
    UNRECONCILED_BANK_RECEIPTS,
    FUND_SUBSCRIPTIONS_PAYABLE,
    FUND_INVESTMENT_CASH_CLEARING,
    FUND_UNITS_OUTSTANDING,
    REDEMPTION_PAYABLE,
    PAYOUTS_CASH_CLEARING
  }

  public enum SavingsFundTransactionType {
    PAYMENT_RECEIVED,
    UNATTRIBUTED_PAYMENT,
    PAYMENT_BOUNCE_BACK,
    FUND_SUBSCRIPTION,
    FUND_TRANSFER,
    LATE_ATTRIBUTION,
    REDEMPTION_REQUEST,
    FUND_CASH_TRANSFER,
    REDEMPTION_PAYOUT
  }

  @Transactional
  public LedgerTransaction recordPaymentReceived(
      User user, BigDecimal amount, String externalReference) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashAccount = getUserCashAccount(userParty);
    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY);

    Map<String, Object> metadata =
        Map.of(
            "operationType", SavingsFundTransactionType.PAYMENT_RECEIVED.name(),
            "userId", user.getId(),
            "personalCode", user.getPersonalCode(),
            "externalReference", externalReference);

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        entry(userCashAccount, amount),
        entry(incomingPaymentsAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordUnattributedPayment(
      BigDecimal amount, String payerIban, String externalReference) {
    LedgerAccount unreconciledAccount =
        getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET);
    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY);

    Map<String, Object> metadata =
        Map.of(
            "operationType", SavingsFundTransactionType.UNATTRIBUTED_PAYMENT.name(),
            "payerIban", payerIban,
            "externalReference", externalReference);

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        entry(unreconciledAccount, amount),
        entry(incomingPaymentsAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction issueFundUnits(
      User user, BigDecimal cashAmount, BigDecimal fundUnits, BigDecimal navPerUnit) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashAccount = getUserCashAccount(userParty);
    LedgerAccount userUnitsAccount = getUserUnitsAccount(userParty);
    LedgerAccount subscriptionsPayableAccount =
        getSystemAccount(FUND_SUBSCRIPTIONS_PAYABLE, EUR, LIABILITY);
    LedgerAccount unitsOutstandingAccount =
        getSystemAccount(FUND_UNITS_OUTSTANDING, FUND_UNIT, LIABILITY);

    Map<String, Object> metadata =
        Map.of(
            "operationType", SavingsFundTransactionType.FUND_SUBSCRIPTION.name(),
            "userId", user.getId(),
            "personalCode", user.getPersonalCode(),
            "navPerUnit", navPerUnit);

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        entry(userCashAccount, cashAmount.negate()),
        entry(subscriptionsPayableAccount, cashAmount),
        entry(userUnitsAccount, fundUnits),
        entry(unitsOutstandingAccount, fundUnits.negate()));
  }

  @Transactional
  public LedgerTransaction transferToFundAccount(BigDecimal amount) {
    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY);
    LedgerAccount fundCashAccount =
        getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET);

    Map<String, Object> metadata =
        Map.of("operationType", SavingsFundTransactionType.FUND_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        entry(incomingPaymentsAccount, amount),
        entry(fundCashAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction bounceBackUnattributedPayment(BigDecimal amount, String payerIban) {
    LedgerAccount unreconciledAccount =
        getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET);
    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY);

    Map<String, Object> metadata =
        Map.of(
            "operationType",
            SavingsFundTransactionType.PAYMENT_BOUNCE_BACK.name(),
            "payerIban",
            payerIban);

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        entry(unreconciledAccount, amount.negate()),
        entry(incomingPaymentsAccount, amount));
  }

  @Transactional
  public LedgerTransaction attributeLatePayment(User user, BigDecimal amount) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userCashAccount = getUserCashAccount(userParty);
    LedgerAccount unreconciledAccount =
        getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET);

    Map<String, Object> metadata =
        Map.of(
            "operationType", SavingsFundTransactionType.LATE_ATTRIBUTION.name(),
            "userId", user.getId(),
            "personalCode", user.getPersonalCode());

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        entry(unreconciledAccount, amount.negate()),
        entry(userCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction processRedemption(
      User user, BigDecimal fundUnits, BigDecimal cashAmount, BigDecimal navPerUnit) {
    LedgerParty userParty = getUserParty(user);
    LedgerAccount userUnitsAccount = getUserUnitsAccount(userParty);
    LedgerAccount unitsOutstandingAccount =
        getSystemAccount(FUND_UNITS_OUTSTANDING, FUND_UNIT, LIABILITY);
    LedgerAccount redemptionPayableAccount =
        getSystemAccount(REDEMPTION_PAYABLE, EUR, LIABILITY);
    LedgerAccount fundCashAccount =
        getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET);

    Map<String, Object> metadata =
        Map.of(
            "operationType", SavingsFundTransactionType.REDEMPTION_REQUEST.name(),
            "userId", user.getId(),
            "personalCode", user.getPersonalCode(),
            "navPerUnit", navPerUnit);

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        entry(userUnitsAccount, fundUnits.negate()),
        entry(unitsOutstandingAccount, fundUnits),
        entry(redemptionPayableAccount, cashAmount),
        entry(fundCashAccount, cashAmount.negate()));
  }

  @Transactional
  public LedgerTransaction transferFundToPayoutCash(BigDecimal amount) {
    LedgerAccount fundCashAccount =
        getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET);
    LedgerAccount payoutsCashAccount =
        getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET);

    Map<String, Object> metadata =
        Map.of("operationType", SavingsFundTransactionType.FUND_CASH_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        entry(fundCashAccount, amount),
        entry(payoutsCashAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction processRedemptionPayout(
      User user, BigDecimal amount, String customerIban) {
    LedgerAccount payoutsCashAccount =
        getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET);
    LedgerAccount redemptionPayableAccount =
        getSystemAccount(REDEMPTION_PAYABLE, EUR, LIABILITY);

    Map<String, Object> metadata =
        Map.of(
            "operationType", SavingsFundTransactionType.REDEMPTION_PAYOUT.name(),
            "userId", user.getId(),
            "personalCode", user.getPersonalCode(),
            "customerIban", customerIban);

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        entry(payoutsCashAccount, amount),
        entry(redemptionPayableAccount, amount.negate()));
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }

  private LedgerParty getUserParty(User user) {
    return ledgerPartyService
        .getPartyForUser(user)
        .orElseThrow(
            () -> new IllegalStateException("User not onboarded: " + user.getPersonalCode()));
  }

  private LedgerAccount getUserCashAccount(LedgerParty userParty) {
    return ledgerAccountRepository
        .findByOwnerAndAccountTypeAndAssetTypeWithEntries(userParty, INCOME, EUR)
        .orElseThrow(() -> new IllegalStateException("User cash account not found"));
  }

  private LedgerAccount getUserUnitsAccount(LedgerParty userParty) {
    return ledgerAccountService
        .getLedgerAccount(userParty, ASSET, FUND_UNIT)
        .orElseThrow(() -> new IllegalStateException("User units account not found"));
  }

  private LedgerAccount getSystemAccount(
      SystemAccount systemAccount,
      LedgerAccount.AssetType assetType,
      LedgerAccount.AccountType accountType) {
    return ledgerAccountRepository
        .findByNameAndPurposeAndAssetTypeAndAccountTypeWithEntries(
            systemAccount.name(), SYSTEM_ACCOUNT, assetType, accountType)
        .orElseGet(
            () ->
                ledgerAccountService.createSystemAccount(
                    systemAccount.name(), SYSTEM_ACCOUNT, assetType, accountType));
  }
}
