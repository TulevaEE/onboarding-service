package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static java.math.BigDecimal.ZERO;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class SavingsFundLedgerTest {

  @Autowired LedgerService ledgerService;
  @Autowired SavingsFundLedger savingsFundLedger;

  User testUser = sampleUser().personalCode("38001010001").build();

  @Test
  @DisplayName("Money-in flow: Payment received should create correct ledger entries")
  void shouldRecordPaymentReceived() {
    BigDecimal amount = new BigDecimal("1000.00");
    UUID externalReference = randomUUID();

    LedgerTransaction transaction =
        savingsFundLedger.recordPaymentReceived(testUser, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RECEIVED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(transaction.getMetadata().get("personalCode")).isEqualTo(testUser.getPersonalCode());

    // Verify user cash account balance liability increased
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(amount.negate());

    // Verify incoming payments clearing asset increased
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(amount);

    // Verify double-entry accounting is maintained
    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Reconciliation flow: Unattributed payment should be recorded separately")
  void testRecordUnattributedPayment() {
    BigDecimal amount = new BigDecimal("500.00");
    UUID externalReference = randomUUID();

    LedgerTransaction transaction =
        savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("UNATTRIBUTED_PAYMENT");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);

    assertThat(getUnreconciledBankReceiptsAccount().getBalance())
        .isEqualByComparingTo(amount.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(amount);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Reconciliation flow: Late attribution should transfer from unreconciled to user")
  void testAttributeLatePayment() {
    BigDecimal amount = new BigDecimal("750.00");
    savingsFundLedger.recordUnattributedPayment(amount, randomUUID());

    LedgerTransaction transaction = savingsFundLedger.attributeLatePayment(testUser, amount);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("LATE_ATTRIBUTION");
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(transaction.getMetadata().get("personalCode")).isEqualTo(testUser.getPersonalCode());

    assertThat(getUnreconciledBankReceiptsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(amount.negate());

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Reconciliation flow: Bounce-back should reverse unattributed payment")
  void testBounceBackUnattributedPayment() {
    BigDecimal amount = new BigDecimal("300.00");
    UUID externalReference = randomUUID();
    savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    LedgerTransaction transaction =
        savingsFundLedger.bounceBackUnattributedPayment(amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_BOUNCE_BACK");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);

    assertThat(getUnreconciledBankReceiptsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Subscription flow: Should reserve payment before issuing fund units")
  void testReservePaymentAndIssueFundUnits() {
    BigDecimal cashAmount = new BigDecimal("950.00");
    BigDecimal fundUnits = new BigDecimal("10.00000");
    BigDecimal navPerUnit = new BigDecimal("95.00");

    // Step 1: Payment received
    savingsFundLedger.recordPaymentReceived(testUser, cashAmount, randomUUID());
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(cashAmount.negate());

    // Step 2: Reserve payment for subscription (cash -> cash_reserved)
    LedgerTransaction reserveTransaction =
        savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount);
    assertThat(reserveTransaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RESERVED");
    assertThat(reserveTransaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(cashAmount.negate());

    // Step 3: Issue fund units from reserved account
    LedgerTransaction subscriptionTransaction =
        savingsFundLedger.issueFundUnitsFromReserved(testUser, cashAmount, fundUnits, navPerUnit);

    assertThat(subscriptionTransaction.getMetadata().get("operationType"))
        .isEqualTo("FUND_SUBSCRIPTION");
    assertThat(subscriptionTransaction.getMetadata().get("navPerUnit")).isEqualTo(navPerUnit);
    assertThat(subscriptionTransaction.getMetadata().get("userId")).isEqualTo(testUser.getId());

    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserUnitsAccount().getBalance()).isEqualByComparingTo(fundUnits.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(cashAmount);
    assertThat(getFundUnitsOutstandingAccount().getBalance()).isEqualByComparingTo(fundUnits);
    assertThat(getUserSubscriptionsAccount().getBalance())
        .isEqualByComparingTo(cashAmount.negate());

    verifyDoubleEntry(reserveTransaction);
    verifyDoubleEntry(subscriptionTransaction);
  }

  @Test
  @DisplayName("Subscription flow: Should transfer cash to fund investment account")
  void testTransferToFundAccount() {
    BigDecimal amount = new BigDecimal("2000.00");
    savingsFundLedger.recordPaymentReceived(testUser, amount, randomUUID());

    LedgerTransaction transaction = savingsFundLedger.transferToFundAccount(amount);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("FUND_TRANSFER");

    LedgerAccount incomingAccount = getIncomingPaymentsClearingAccount();
    assertThat(incomingAccount.getBalance()).isEqualByComparingTo(ZERO);

    LedgerAccount fundAccount = getFundInvestmentCashClearingAccount();
    assertThat(fundAccount.getBalance()).isEqualByComparingTo(amount);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Redemption flow: Should transfer fund cash to payout clearing")
  void testTransferFundToPayoutCash() {
    BigDecimal amount = new BigDecimal("1200.00");
    setupFundWithCash(amount);

    LedgerTransaction transaction = savingsFundLedger.transferFundToPayoutCash(amount);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("FUND_CASH_TRANSFER");

    assertThat(getFundInvestmentCashClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getPayoutsCashClearingAccount().getBalance()).isEqualByComparingTo(amount);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Complete subscription flow: Payment → Reserve → Units → Fund transfer")
  void testCompleteSubscriptionFlow() {
    BigDecimal paymentAmount = new BigDecimal("1000.00");
    BigDecimal fundUnits = new BigDecimal("10.52630");
    BigDecimal navPerUnit = new BigDecimal("95.00");

    LedgerTransaction paymentTx =
        savingsFundLedger.recordPaymentReceived(testUser, paymentAmount, randomUUID());
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
    assertThat(getFundInvestmentCashClearingAccount().getBalance())
        .isEqualByComparingTo(paymentAmount);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserSubscriptionsAccount().getBalance())
        .isEqualByComparingTo(paymentAmount.negate());
  }

  @Test
  @DisplayName("Complete redemption flow: Request → Cash transfer → Payout")
  void testCompleteRedemptionFlow() {
    BigDecimal initialAmount = new BigDecimal("1000.00");
    BigDecimal initialUnits = new BigDecimal("10.00000");
    BigDecimal redeemUnits = new BigDecimal("3.00000");
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
    assertThat(getUserRedemptionsAccount().getBalance()).isEqualByComparingTo(redeemAmount);
    assertThat(getPayoutsCashClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  @Test
  @DisplayName("Redemption flow: Should process final payout to customer")
  void testProcessRedemptionPayoutFromCashRedemption() {
    BigDecimal amount = new BigDecimal("500.00");
    String customerIban = "EE111222333444555666";
    setupRedemptionScenario(amount);

    LedgerTransaction transaction =
        savingsFundLedger.processRedemptionPayoutFromCashRedemption(testUser, amount, customerIban);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("REDEMPTION_PAYOUT");
    assertThat(transaction.getMetadata().get("customerIban")).isEqualTo(customerIban);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());

    // After payout, user's cash redemption account should be empty
    assertThat(getUserCashRedemptionAccount().getBalance()).isEqualByComparingTo(ZERO);

    // Payouts cash clearing should also be zero (money sent out)
    assertThat(getPayoutsCashClearingAccount().getBalance()).isEqualByComparingTo(ZERO);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("hasLedgerEntry returns false for non-existent entries and true after recording")
  void testHasLedgerEntry() {
    BigDecimal amount = new BigDecimal("500.00");
    UUID externalReference = randomUUID();

    assertThat(savingsFundLedger.hasLedgerEntry(externalReference)).isFalse();

    savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    assertThat(savingsFundLedger.hasLedgerEntry(externalReference)).isTrue();
    assertThat(savingsFundLedger.hasLedgerEntry(randomUUID())).isFalse();
  }

  @Test
  @DisplayName("hasLedgerEntry detects bounce back entries")
  void testHasLedgerEntry_forBounceBack() {
    BigDecimal amount = new BigDecimal("300.00");
    UUID paymentId = randomUUID();
    UUID bounceBackId = randomUUID();

    assertThat(savingsFundLedger.hasLedgerEntry(paymentId)).isFalse();
    assertThat(savingsFundLedger.hasLedgerEntry(bounceBackId)).isFalse();

    savingsFundLedger.recordUnattributedPayment(amount, paymentId);
    assertThat(savingsFundLedger.hasLedgerEntry(paymentId)).isTrue();

    savingsFundLedger.bounceBackUnattributedPayment(amount, bounceBackId);
    assertThat(savingsFundLedger.hasLedgerEntry(bounceBackId)).isTrue();
  }

  @Test
  @DisplayName("hasLedgerEntry detects payment received entries")
  void testHasLedgerEntry_forPaymentReceived() {
    BigDecimal amount = new BigDecimal("1000.00");
    UUID externalReference = randomUUID();

    assertThat(savingsFundLedger.hasLedgerEntry(externalReference)).isFalse();

    savingsFundLedger.recordPaymentReceived(testUser, amount, externalReference);

    assertThat(savingsFundLedger.hasLedgerEntry(externalReference)).isTrue();
  }

  @Test
  @DisplayName("Should auto-create party and accounts for non-onboarded user")
  void testAutoCreatePartyForUnonboardedUser() {
    User unonboardedUser = sampleUser().personalCode("99999999999").build();
    BigDecimal amount = new BigDecimal("100.00");
    UUID externalReference = randomUUID();

    // Should not throw exception, should create party and accounts automatically
    LedgerTransaction transaction =
        savingsFundLedger.recordPaymentReceived(unonboardedUser, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RECEIVED");
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(unonboardedUser.getId());
    assertThat(transaction.getMetadata().get("personalCode"))
        .isEqualTo(unonboardedUser.getPersonalCode());
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);

    // Verify double-entry accounting is maintained
    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Should maintain accounting relationships after complex operations")
  void testMaintainAccountingRelationshipsAfterComplexOperations() {
    BigDecimal amount = new BigDecimal("1000.00");

    LedgerTransaction payment =
        savingsFundLedger.recordPaymentReceived(testUser, amount, randomUUID());
    LedgerTransaction reserve = savingsFundLedger.reservePaymentForSubscription(testUser, amount);
    LedgerTransaction subscription =
        savingsFundLedger.issueFundUnitsFromReserved(
            testUser, amount, new BigDecimal("10.00000"), new BigDecimal("100.00"));
    LedgerTransaction transfer = savingsFundLedger.transferToFundAccount(amount);

    verifyDoubleEntry(payment);
    verifyDoubleEntry(reserve);
    verifyDoubleEntry(subscription);
    verifyDoubleEntry(transfer);

    LedgerAccount userCashAccount = getUserCashAccount();
    assertThat(userCashAccount.getEntries()).isNotEmpty();
    assertThat(userCashAccount.getBalance()).isEqualByComparingTo(ZERO);

    LedgerAccount userCashReservedAccount = getUserCashReservedAccount();
    assertThat(userCashReservedAccount.getEntries()).isNotEmpty();
    assertThat(userCashReservedAccount.getBalance()).isEqualByComparingTo(ZERO);

    LedgerAccount userUnitsAccount = getUserUnitsAccount();
    assertThat(userUnitsAccount.getEntries()).isNotEmpty();
    assertThat(userUnitsAccount.getBalance()).isEqualByComparingTo(new BigDecimal("-10.00000"));

    LedgerAccount fundSubscriptionsAccount = getUserSubscriptionsAccount();
    assertThat(fundSubscriptionsAccount.getEntries()).isNotEmpty();
    assertThat(fundSubscriptionsAccount.getBalance()).isEqualByComparingTo(amount.negate());

    LedgerAccount fundInvestmentAccount = getFundInvestmentCashClearingAccount();
    assertThat(fundInvestmentAccount.getEntries()).isNotEmpty();
    assertThat(fundInvestmentAccount.getBalance()).isEqualByComparingTo(amount);

    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();
    assertThat(incomingPaymentsAccount.getEntries()).isNotEmpty();
    assertThat(incomingPaymentsAccount.getBalance()).isEqualByComparingTo(ZERO);
  }

  // Helper methods

  private void setupUserWithFundUnits(
      BigDecimal cashAmount, BigDecimal fundUnits, BigDecimal navPerUnit) {
    savingsFundLedger.recordPaymentReceived(testUser, cashAmount, randomUUID());
    savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount);
    savingsFundLedger.issueFundUnitsFromReserved(testUser, cashAmount, fundUnits, navPerUnit);
    savingsFundLedger.transferToFundAccount(cashAmount);
  }

  private void setupFundWithCash(BigDecimal amount) {
    savingsFundLedger.recordPaymentReceived(testUser, amount, randomUUID());
    savingsFundLedger.transferToFundAccount(amount);
  }

  private void setupRedemptionScenario(BigDecimal amount) {
    setupUserWithFundUnits(amount, new BigDecimal("5.00000"), new BigDecimal("100.00"));
    savingsFundLedger.reserveFundUnitsForRedemption(testUser, new BigDecimal("5.00000"));
    savingsFundLedger.processRedemptionFromReserved(
        testUser, new BigDecimal("5.00000"), amount, new BigDecimal("100.00"));
    savingsFundLedger.transferFundToPayoutCash(amount);
  }

  // Low-level core helper methods
  private LedgerAccount getUserAccount(UserAccount userAccount) {
    return ledgerService.getUserAccount(testUser, userAccount);
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerService.getSystemAccount(systemAccount);
  }

  // High-level user account helpers
  private LedgerAccount getUserCashAccount() {
    return getUserAccount(CASH);
  }

  private LedgerAccount getUserCashReservedAccount() {
    return getUserAccount(CASH_RESERVED);
  }

  private LedgerAccount getUserCashRedemptionAccount() {
    return getUserAccount(CASH_REDEMPTION);
  }

  private LedgerAccount getUserUnitsAccount() {
    return getUserAccount(FUND_UNITS);
  }

  private LedgerAccount getUserReservedUnitsAccount() {
    return getUserAccount(FUND_UNITS_RESERVED);
  }

  private LedgerAccount getUserSubscriptionsAccount() {
    return getUserAccount(SUBSCRIPTIONS);
  }

  private LedgerAccount getUserRedemptionsAccount() {
    return getUserAccount(REDEMPTIONS);
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
