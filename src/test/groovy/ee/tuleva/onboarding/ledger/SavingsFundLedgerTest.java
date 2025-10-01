package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.SavingsFundLedger.SystemAccount;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class SavingsFundLedgerTest {

  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired LedgerService ledgerService;
  @Autowired LedgerPartyService ledgerPartyService;
  @Autowired LedgerAccountRepository ledgerAccountRepository;
  @Autowired LedgerPartyRepository ledgerPartyRepository;
  @Autowired LedgerTransactionRepository ledgerTransactionRepository;

  User testUser;
  LedgerParty userParty;

  @BeforeEach
  void setUp() {
    testUser = sampleUser().personalCode("38001010001").build();
    ledgerService.onboard(testUser);
    userParty = ledgerPartyService.getParty(testUser).orElseThrow();
  }

  @AfterEach
  void cleanup() {
    ledgerTransactionRepository.deleteAll();
    ledgerAccountRepository.deleteAll();
    ledgerPartyRepository.deleteAll();
  }

  @Test
  @DisplayName("Money-in flow: Payment received should create correct ledger entries")
  void shouldRecordPaymentReceived() {
    BigDecimal amount = new BigDecimal("1000.00");
    String externalReference = "MONTONIO_123456";

    LedgerTransaction transaction =
        savingsFundLedger.recordPaymentReceived(testUser, amount, externalReference);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RECEIVED");
    assertThat(transaction.getMetadata().get("externalReference")).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(transaction.getMetadata().get("personalCode")).isEqualTo(testUser.getPersonalCode());

    // Verify user cash account balance liability increased
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(amount.negate());

    // Verify incoming payments clearing asset increased
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(amount);

    // Verify double-entry accounting is maintained
    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Reconciliation flow: Unattributed payment should be recorded separately")
  void testRecordUnattributedPayment() {
    BigDecimal amount = new BigDecimal("500.00");
    String payerIban = "EE123456789012345678";
    String externalReference = "UNATTRIBUTED_789";

    LedgerTransaction transaction =
        savingsFundLedger.recordUnattributedPayment(amount, payerIban, externalReference);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("UNATTRIBUTED_PAYMENT");
    assertThat(transaction.getMetadata().get("payerIban")).isEqualTo(payerIban);
    assertThat(transaction.getMetadata().get("externalReference")).isEqualTo(externalReference);

    assertThat(getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET).getBalance())
        .isEqualByComparingTo(amount.negate());
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(amount);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Reconciliation flow: Late attribution should transfer from unreconciled to user")
  void testAttributeLatePayment() {
    BigDecimal amount = new BigDecimal("750.00");
    String payerIban = "EE987654321098765432";
    savingsFundLedger.recordUnattributedPayment(amount, payerIban, "LATE_REF");

    LedgerTransaction transaction = savingsFundLedger.attributeLatePayment(testUser, amount);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("LATE_ATTRIBUTION");
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(transaction.getMetadata().get("personalCode")).isEqualTo(testUser.getPersonalCode());

    assertThat(getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(amount.negate());

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Reconciliation flow: Bounce-back should reverse unattributed payment")
  void testBounceBackUnattributedPayment() {
    BigDecimal amount = new BigDecimal("300.00");
    String payerIban = "EE555666777888999000";
    savingsFundLedger.recordUnattributedPayment(amount, payerIban, "BOUNCE_REF");

    LedgerTransaction transaction =
        savingsFundLedger.bounceBackUnattributedPayment(amount, payerIban);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_BOUNCE_BACK");
    assertThat(transaction.getMetadata().get("payerIban")).isEqualTo(payerIban);

    assertThat(getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Subscription flow: Should reserve payment before issuing fund units")
  void testReservePaymentAndIssueFundUnits() {
    BigDecimal cashAmount = new BigDecimal("950.00");
    BigDecimal fundUnits = new BigDecimal("10.0000");
    BigDecimal navPerUnit = new BigDecimal("95.00");

    // Step 1: Payment received
    savingsFundLedger.recordPaymentReceived(testUser, cashAmount, "SETUP_REF");
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(cashAmount.negate());

    // Step 2: Reserve payment for subscription (cash -> cash_reserved)
    LedgerTransaction reserveTransaction =
        savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount);
    assertThat(reserveTransaction).isNotNull();
    assertThat(reserveTransaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RESERVED");
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(cashAmount.negate());

    // Step 3: Issue fund units from reserved account
    LedgerTransaction subscriptionTransaction =
        savingsFundLedger.issueFundUnitsFromReserved(testUser, cashAmount, fundUnits, navPerUnit);

    assertThat(subscriptionTransaction).isNotNull();
    assertThat(subscriptionTransaction.getMetadata().get("operationType"))
        .isEqualTo("FUND_SUBSCRIPTION");
    assertThat(subscriptionTransaction.getMetadata().get("navPerUnit")).isEqualTo(navPerUnit);
    assertThat(subscriptionTransaction.getMetadata().get("userId")).isEqualTo(testUser.getId());

    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserUnitsAccount().getBalance()).isEqualByComparingTo(fundUnits.negate());
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(cashAmount);
    assertThat(getSystemAccount(FUND_UNITS_OUTSTANDING, FUND_UNIT, LIABILITY).getBalance())
        .isEqualByComparingTo(fundUnits);
    assertThat(getSystemAccount(SUBSCRIPTIONS, EUR, INCOME).getBalance())
        .isEqualByComparingTo(cashAmount.negate());

    verifyDoubleEntry(reserveTransaction);
    verifyDoubleEntry(subscriptionTransaction);
  }

  @Test
  @DisplayName("Subscription flow: Should transfer cash to fund investment account")
  void testTransferToFundAccount() {
    BigDecimal amount = new BigDecimal("2000.00");
    savingsFundLedger.recordPaymentReceived(testUser, amount, "FUND_TRANSFER_REF");

    LedgerTransaction transaction = savingsFundLedger.transferToFundAccount(amount);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("FUND_TRANSFER");

    LedgerAccount incomingAccount = getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET);
    assertThat(incomingAccount.getBalance()).isEqualByComparingTo(ZERO);

    LedgerAccount fundAccount = getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET);
    assertThat(fundAccount.getBalance()).isEqualByComparingTo(amount);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Redemption flow: Should transfer fund cash to payout clearing")
  void testTransferFundToPayoutCash() {
    BigDecimal amount = new BigDecimal("1200.00");
    setupFundWithCash(amount);

    LedgerTransaction transaction = savingsFundLedger.transferFundToPayoutCash(amount);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("FUND_CASH_TRANSFER");

    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(amount);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Complete subscription flow: Payment → Reserve → Units → Fund transfer")
  void testCompleteSubscriptionFlow() {
    BigDecimal paymentAmount = new BigDecimal("1000.00");
    BigDecimal fundUnits = new BigDecimal("10.5263");
    BigDecimal navPerUnit = new BigDecimal("95.00");

    LedgerTransaction paymentTx =
        savingsFundLedger.recordPaymentReceived(testUser, paymentAmount, "COMPLETE_FLOW_REF");
    LedgerTransaction reserveTx =
        savingsFundLedger.reservePaymentForSubscription(testUser, paymentAmount);
    LedgerTransaction subscriptionTx =
        savingsFundLedger.issueFundUnitsFromReserved(
            testUser, paymentAmount, fundUnits, navPerUnit);
    LedgerTransaction transferTx = savingsFundLedger.transferToFundAccount(paymentAmount);

    verifyDoubleEntry(paymentTx);
    verifyDoubleEntry(reserveTx);
    verifyDoubleEntry(subscriptionTx);
    verifyDoubleEntry(transferTx);

    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserUnitsAccount().getBalance()).isEqualByComparingTo(fundUnits.negate());
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(paymentAmount);
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(SUBSCRIPTIONS, EUR, INCOME).getBalance())
        .isEqualByComparingTo(paymentAmount.negate());
  }

  @Test
  @DisplayName("Complete redemption flow: Request → Cash transfer → Payout")
  void testCompleteRedemptionFlow() {
    BigDecimal initialAmount = new BigDecimal("1000.00");
    BigDecimal initialUnits = new BigDecimal("10.0000");
    BigDecimal redeemUnits = new BigDecimal("3.0000");
    BigDecimal redeemAmount = new BigDecimal("300.00");
    BigDecimal navPerUnit = new BigDecimal("100.00");
    String customerIban = "EE777888999000111222";
    setupUserWithFundUnits(initialAmount, initialUnits, navPerUnit);

    LedgerTransaction reserveTx =
        savingsFundLedger.reserveFundUnitsForRedemption(testUser, redeemUnits);
    LedgerTransaction redemptionTx =
        savingsFundLedger.processRedemptionFromReserved(
            testUser, redeemUnits, redeemAmount, navPerUnit);
    LedgerTransaction cashTransferTx = savingsFundLedger.transferFundToPayoutCash(redeemAmount);
    LedgerTransaction payoutTx =
        savingsFundLedger.processRedemptionPayoutFromCashRedemption(
            testUser, redeemAmount, customerIban);

    verifyDoubleEntry(reserveTx);
    verifyDoubleEntry(redemptionTx);
    verifyDoubleEntry(cashTransferTx);
    verifyDoubleEntry(payoutTx);

    assertThat(getUserUnitsAccount().getBalance())
        .isEqualByComparingTo(initialUnits.negate().add(redeemUnits));
    assertThat(getUserReservedUnitsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(REDEMPTIONS, EUR, EXPENSE).getBalance())
        .isEqualByComparingTo(redeemAmount);
    assertThat(getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
  }

  @Test
  @DisplayName("Redemption flow: Should process final payout to customer")
  void testProcessRedemptionPayoutFromCashRedemption() {
    BigDecimal amount = new BigDecimal("500.00");
    String customerIban = "EE111222333444555666";
    setupRedemptionScenario(amount);

    LedgerTransaction transaction =
        savingsFundLedger.processRedemptionPayoutFromCashRedemption(testUser, amount, customerIban);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("REDEMPTION_PAYOUT");
    assertThat(transaction.getMetadata().get("customerIban")).isEqualTo(customerIban);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());

    // After payout, user's cash redemption account should be empty
    LedgerAccount userCashRedemptionAccount =
        ledgerAccountRepository
            .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
                userParty, CASH_REDEMPTION.name(), USER_ACCOUNT, EUR, LIABILITY)
            .orElseThrow();
    assertThat(userCashRedemptionAccount.getBalance()).isEqualByComparingTo(ZERO);

    // Payouts cash clearing should also be zero (money sent out)
    assertThat(getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Should throw exception for user not onboarded")
  void testThrowExceptionForUnonboardedUser() {
    User unonboardedUser = sampleUser().personalCode("99999999999").build();

    assertThatThrownBy(
            () -> savingsFundLedger.recordPaymentReceived(unonboardedUser, BigDecimal.TEN, "REF"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("User not onboarded");
  }

  @Test
  @DisplayName("Should maintain accounting relationships after complex operations")
  void testMaintainAccountingRelationshipsAfterComplexOperations() {
    BigDecimal amount = new BigDecimal("1000.00");

    LedgerTransaction payment =
        savingsFundLedger.recordPaymentReceived(testUser, amount, "BALANCE_TEST");
    LedgerTransaction reserve = savingsFundLedger.reservePaymentForSubscription(testUser, amount);
    LedgerTransaction subscription =
        savingsFundLedger.issueFundUnitsFromReserved(
            testUser, amount, new BigDecimal("10.0"), new BigDecimal("100.00"));
    LedgerTransaction transfer = savingsFundLedger.transferToFundAccount(amount);

    verifyDoubleEntry(payment);
    verifyDoubleEntry(reserve);
    verifyDoubleEntry(subscription);
    verifyDoubleEntry(transfer);

    LedgerAccount userCashAccount = getUserCashAccount();
    assertThat(userCashAccount.getEntries()).isNotNull();
    assertThat(userCashAccount.getBalance()).isEqualByComparingTo(ZERO);

    LedgerAccount userCashReservedAccount = getUserCashReservedAccount();
    assertThat(userCashReservedAccount.getEntries()).isNotNull();
    assertThat(userCashReservedAccount.getBalance()).isEqualByComparingTo(ZERO);

    LedgerAccount userUnitsAccount = getUserUnitsAccount();
    assertThat(userUnitsAccount.getEntries()).isNotNull();
    assertThat(userUnitsAccount.getBalance()).isEqualByComparingTo(new BigDecimal("-10.0"));

    LedgerAccount fundSubscriptionsAccount = getSystemAccount(SUBSCRIPTIONS, EUR, INCOME);
    assertThat(fundSubscriptionsAccount.getEntries()).isNotNull();
    assertThat(fundSubscriptionsAccount.getBalance()).isEqualByComparingTo(amount.negate());

    LedgerAccount fundInvestmentAccount =
        getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET);
    assertThat(fundInvestmentAccount.getEntries()).isNotNull();
    assertThat(fundInvestmentAccount.getBalance()).isEqualByComparingTo(amount);

    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, ASSET);
    assertThat(incomingPaymentsAccount.getEntries()).isNotNull();
    assertThat(incomingPaymentsAccount.getBalance()).isEqualByComparingTo(ZERO);
  }

  // Helper methods

  private void setupUserWithFundUnits(
      BigDecimal cashAmount, BigDecimal fundUnits, BigDecimal navPerUnit) {
    savingsFundLedger.recordPaymentReceived(testUser, cashAmount, "SETUP_PAYMENT");
    savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount);
    savingsFundLedger.issueFundUnitsFromReserved(testUser, cashAmount, fundUnits, navPerUnit);
    savingsFundLedger.transferToFundAccount(cashAmount);
  }

  private void setupFundWithCash(BigDecimal amount) {
    savingsFundLedger.recordPaymentReceived(testUser, amount, "FUND_SETUP");
    savingsFundLedger.transferToFundAccount(amount);
  }

  private void setupRedemptionScenario(BigDecimal amount) {
    setupUserWithFundUnits(amount, new BigDecimal("5.0"), new BigDecimal("100.00"));
    savingsFundLedger.reserveFundUnitsForRedemption(testUser, new BigDecimal("5.0"));
    savingsFundLedger.processRedemptionFromReserved(
        testUser, new BigDecimal("5.0"), amount, new BigDecimal("100.00"));
    savingsFundLedger.transferFundToPayoutCash(amount);
  }

  private LedgerAccount getUserCashAccount() {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            userParty, CASH.name(), USER_ACCOUNT, EUR, LIABILITY)
        .orElseThrow();
  }

  private LedgerAccount getUserCashReservedAccount() {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            userParty, CASH_RESERVED.name(), USER_ACCOUNT, EUR, LIABILITY)
        .orElseThrow();
  }

  private LedgerAccount getUserUnitsAccount() {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            userParty, FUND_UNITS.name(), USER_ACCOUNT, FUND_UNIT, LIABILITY)
        .orElseThrow();
  }

  private LedgerAccount getUserReservedUnitsAccount() {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            userParty, FUND_UNITS_RESERVED.name(), USER_ACCOUNT, FUND_UNIT, LIABILITY)
        .orElseThrow();
  }

  private LedgerAccount getSystemAccount(
      SystemAccount systemAccount, AssetType assetType, AccountType accountType) {
    return ledgerAccountRepository
        .findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
            null, systemAccount.name(), SYSTEM_ACCOUNT, assetType, accountType)
        .orElseThrow();
  }

  private static void verifyDoubleEntry(LedgerTransaction transaction) {
    List<LedgerEntry> entries = transaction.getEntries();

    assertThat(entries.size()).isGreaterThan(1);

    BigDecimal totalDebits =
        entries.stream()
            .map(LedgerEntry::getAmount)
            .filter(amount -> amount.compareTo(ZERO) > 0)
            .reduce(ZERO, BigDecimal::add);

    BigDecimal totalCredits =
        entries.stream()
            .filter(entry -> entry.getAmount().compareTo(ZERO) < 0)
            .map(entry -> entry.getAmount().abs())
            .reduce(ZERO, BigDecimal::add);

    assertThat(totalDebits.compareTo(totalCredits)).isEqualTo(0);
  }
}
