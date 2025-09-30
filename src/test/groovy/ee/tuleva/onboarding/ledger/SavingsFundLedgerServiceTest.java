package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.SavingsFundLedgerService.SystemAccount.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.SavingsFundLedgerService.SystemAccount;
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
class SavingsFundLedgerServiceTest {

  @Autowired SavingsFundLedgerService savingsFundLedgerService;

  @Autowired LedgerService ledgerService;

  @Autowired LedgerAccountService ledgerAccountService;

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
        savingsFundLedgerService.recordPaymentReceived(testUser, amount, externalReference);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RECEIVED");
    assertThat(transaction.getMetadata().get("externalReference")).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(transaction.getMetadata().get("personalCode")).isEqualTo(testUser.getPersonalCode());

    // Verify user cash account balance increased
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(amount);

    // Verify incoming payments clearing liability increased
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY).getBalance())
        .isEqualByComparingTo(amount.negate());

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
        savingsFundLedgerService.recordUnattributedPayment(amount, payerIban, externalReference);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("UNATTRIBUTED_PAYMENT");
    assertThat(transaction.getMetadata().get("payerIban")).isEqualTo(payerIban);
    assertThat(transaction.getMetadata().get("externalReference")).isEqualTo(externalReference);

    assertThat(getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY).getBalance())
        .isEqualByComparingTo(amount.negate());

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Reconciliation flow: Late attribution should transfer from unreconciled to user")
  void testAttributeLatePayment() {
    BigDecimal amount = new BigDecimal("750.00");
    String payerIban = "EE987654321098765432";
    savingsFundLedgerService.recordUnattributedPayment(amount, payerIban, "LATE_REF");

    LedgerTransaction transaction = savingsFundLedgerService.attributeLatePayment(testUser, amount);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("LATE_ATTRIBUTION");
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(transaction.getMetadata().get("personalCode")).isEqualTo(testUser.getPersonalCode());

    assertThat(getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(amount);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Reconciliation flow: Bounce-back should reverse unattributed payment")
  void testBounceBackUnattributedPayment() {
    BigDecimal amount = new BigDecimal("300.00");
    String payerIban = "EE555666777888999000";
    savingsFundLedgerService.recordUnattributedPayment(amount, payerIban, "BOUNCE_REF");

    LedgerTransaction transaction =
        savingsFundLedgerService.bounceBackUnattributedPayment(amount, payerIban);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_BOUNCE_BACK");
    assertThat(transaction.getMetadata().get("payerIban")).isEqualTo(payerIban);

    assertThat(getSystemAccount(UNRECONCILED_BANK_RECEIPTS, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY).getBalance())
        .isEqualByComparingTo(ZERO);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Subscription flow: Should issue fund units for user cash")
  void testIssueFundUnits() {
    BigDecimal cashAmount = new BigDecimal("950.00");
    BigDecimal fundUnits = new BigDecimal("10.0000");
    BigDecimal navPerUnit = new BigDecimal("95.00");
    savingsFundLedgerService.recordPaymentReceived(testUser, cashAmount, "SETUP_REF");

    LedgerTransaction transaction =
        savingsFundLedgerService.issueFundUnits(testUser, cashAmount, fundUnits, navPerUnit);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("FUND_SUBSCRIPTION");
    assertThat(transaction.getMetadata().get("navPerUnit")).isEqualTo(navPerUnit);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());

    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserUnitsAccount().getBalance()).isEqualByComparingTo(fundUnits);
    assertThat(getSystemAccount(FUND_SUBSCRIPTIONS_PAYABLE, EUR, LIABILITY).getBalance())
        .isEqualByComparingTo(cashAmount);
    assertThat(getSystemAccount(FUND_UNITS_OUTSTANDING, FUND_UNIT, LIABILITY).getBalance())
        .isEqualByComparingTo(fundUnits.negate());

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Subscription flow: Should transfer cash to fund investment account")
  void testTransferToFundAccount() {
    BigDecimal amount = new BigDecimal("2000.00");
    savingsFundLedgerService.recordPaymentReceived(testUser, amount, "FUND_TRANSFER_REF");

    LedgerTransaction transaction = savingsFundLedgerService.transferToFundAccount(amount);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("FUND_TRANSFER");

    LedgerAccount incomingAccount = getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY);
    assertThat(incomingAccount.getBalance()).isEqualByComparingTo(ZERO);

    LedgerAccount fundAccount = getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET);
    assertThat(fundAccount.getBalance()).isEqualByComparingTo(amount.negate());

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Redemption flow: Should process redemption request correctly")
  void testProcessRedemption() {
    BigDecimal initialCash = new BigDecimal("1000.00");
    BigDecimal initialUnits = new BigDecimal("10.0000");
    BigDecimal redeemUnits = new BigDecimal("4.0000");
    BigDecimal redeemAmount = new BigDecimal("400.00");
    BigDecimal navPerUnit = new BigDecimal("100.00");
    setupUserWithFundUnits(initialCash, initialUnits, navPerUnit);

    LedgerTransaction transaction =
        savingsFundLedgerService.processRedemption(testUser, redeemUnits, redeemAmount, navPerUnit);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("REDEMPTION_REQUEST");
    assertThat(transaction.getMetadata().get("navPerUnit")).isEqualTo(navPerUnit);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());

    assertThat(getUserUnitsAccount().getBalance())
        .isEqualByComparingTo(initialUnits.subtract(redeemUnits));
    assertThat(getSystemAccount(FUND_UNITS_OUTSTANDING, FUND_UNIT, LIABILITY).getBalance())
        .isEqualByComparingTo(initialUnits.negate().add(redeemUnits));
    assertThat(getSystemAccount(REDEMPTION_PAYABLE, EUR, LIABILITY).getBalance())
        .isEqualByComparingTo(redeemAmount);
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(initialCash.negate().subtract(redeemAmount));

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Redemption flow: Should transfer fund cash to payout clearing")
  void testTransferFundToPayoutCash() {
    BigDecimal amount = new BigDecimal("1200.00");
    setupFundWithCash(amount);

    LedgerTransaction transaction = savingsFundLedgerService.transferFundToPayoutCash(amount);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("FUND_CASH_TRANSFER");

    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(amount.negate());

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Redemption flow: Should process final payout to customer")
  void testProcessRedemptionPayout() {
    BigDecimal amount = new BigDecimal("500.00");
    String customerIban = "EE111222333444555666";
    setupRedemptionScenario(amount);

    LedgerTransaction transaction =
        savingsFundLedgerService.processRedemptionPayout(testUser, amount, customerIban);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("REDEMPTION_PAYOUT");
    assertThat(transaction.getMetadata().get("customerIban")).isEqualTo(customerIban);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());

    assertThat(getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(REDEMPTION_PAYABLE, EUR, LIABILITY).getBalance())
        .isEqualByComparingTo(ZERO);

    verifyDoubleEntry(transaction);
  }

  @Test
  @DisplayName("Complete subscription flow: Payment → Units → Fund transfer")
  void testCompleteSubscriptionFlow() {
    BigDecimal paymentAmount = new BigDecimal("1000.00");
    BigDecimal fundUnits = new BigDecimal("10.5263");
    BigDecimal navPerUnit = new BigDecimal("95.00");

    LedgerTransaction paymentTx =
        savingsFundLedgerService.recordPaymentReceived(
            testUser, paymentAmount, "COMPLETE_FLOW_REF");
    LedgerTransaction subscriptionTx =
        savingsFundLedgerService.issueFundUnits(testUser, paymentAmount, fundUnits, navPerUnit);
    LedgerTransaction transferTx = savingsFundLedgerService.transferToFundAccount(paymentAmount);

    verifyDoubleEntry(paymentTx);
    verifyDoubleEntry(subscriptionTx);
    verifyDoubleEntry(transferTx);

    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserUnitsAccount().getBalance()).isEqualByComparingTo(fundUnits);
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(paymentAmount.negate());
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY).getBalance())
        .isEqualByComparingTo(ZERO);
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

    LedgerTransaction redemptionTx =
        savingsFundLedgerService.processRedemption(testUser, redeemUnits, redeemAmount, navPerUnit);
    LedgerTransaction cashTransferTx =
        savingsFundLedgerService.transferFundToPayoutCash(redeemAmount);
    LedgerTransaction payoutTx =
        savingsFundLedgerService.processRedemptionPayout(testUser, redeemAmount, customerIban);

    verifyDoubleEntry(redemptionTx);
    verifyDoubleEntry(cashTransferTx);
    verifyDoubleEntry(payoutTx);

    assertThat(getUserUnitsAccount().getBalance())
        .isEqualByComparingTo(initialUnits.subtract(redeemUnits));
    assertThat(getSystemAccount(REDEMPTION_PAYABLE, EUR, LIABILITY).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(PAYOUTS_CASH_CLEARING, EUR, ASSET).getBalance())
        .isEqualByComparingTo(ZERO);
  }

  @Test
  @DisplayName("Should throw exception for user not onboarded")
  void testThrowExceptionForUnonboardedUser() {
    User unonboardedUser = sampleUser().personalCode("99999999999").build();

    assertThatThrownBy(
            () ->
                savingsFundLedgerService.recordPaymentReceived(
                    unonboardedUser, BigDecimal.TEN, "REF"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("User not onboarded");
  }

  @Test
  @DisplayName("Should maintain accounting relationships after complex operations")
  void testMaintainAccountingRelationshipsAfterComplexOperations() {
    BigDecimal amount = new BigDecimal("1000.00");

    LedgerTransaction payment =
        savingsFundLedgerService.recordPaymentReceived(testUser, amount, "BALANCE_TEST");
    LedgerTransaction subscription =
        savingsFundLedgerService.issueFundUnits(
            testUser, amount, new BigDecimal("10.0"), new BigDecimal("100.00"));
    LedgerTransaction transfer = savingsFundLedgerService.transferToFundAccount(amount);

    verifyDoubleEntry(payment);
    verifyDoubleEntry(subscription);
    verifyDoubleEntry(transfer);

    LedgerAccount userCashAccount = getUserCashAccount();
    assertThat(userCashAccount.getEntries()).isNotNull();
    assertThat(userCashAccount.getBalance()).isEqualByComparingTo(ZERO);

    LedgerAccount userUnitsAccount = getUserUnitsAccount();
    assertThat(userUnitsAccount.getEntries()).isNotNull();
    assertThat(userUnitsAccount.getBalance()).isEqualByComparingTo(new BigDecimal("10.0"));

    LedgerAccount fundSubscriptionsAccount =
        getSystemAccount(FUND_SUBSCRIPTIONS_PAYABLE, EUR, LIABILITY);
    assertThat(fundSubscriptionsAccount.getEntries()).isNotNull();
    assertThat(fundSubscriptionsAccount.getBalance()).isEqualByComparingTo(amount);

    LedgerAccount fundInvestmentAccount =
        getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, EUR, ASSET);
    assertThat(fundInvestmentAccount.getEntries()).isNotNull();
    assertThat(fundInvestmentAccount.getBalance()).isEqualByComparingTo(amount.negate());

    LedgerAccount incomingPaymentsAccount =
        getSystemAccount(INCOMING_PAYMENTS_CLEARING, EUR, LIABILITY);
    assertThat(incomingPaymentsAccount.getEntries()).isNotNull();
    assertThat(incomingPaymentsAccount.getBalance()).isEqualByComparingTo(ZERO);
  }

  // Helper methods

  private void setupUserWithFundUnits(
      BigDecimal cashAmount, BigDecimal fundUnits, BigDecimal navPerUnit) {
    savingsFundLedgerService.recordPaymentReceived(testUser, cashAmount, "SETUP_PAYMENT");
    savingsFundLedgerService.issueFundUnits(testUser, cashAmount, fundUnits, navPerUnit);
    savingsFundLedgerService.transferToFundAccount(cashAmount);
  }

  private void setupFundWithCash(BigDecimal amount) {
    savingsFundLedgerService.recordPaymentReceived(testUser, amount, "FUND_SETUP");
    savingsFundLedgerService.transferToFundAccount(amount);
  }

  private void setupRedemptionScenario(BigDecimal amount) {
    setupUserWithFundUnits(amount, new BigDecimal("5.0"), new BigDecimal("100.00"));
    savingsFundLedgerService.processRedemption(
        testUser, new BigDecimal("5.0"), amount, new BigDecimal("100.00"));
    savingsFundLedgerService.transferFundToPayoutCash(amount);
  }

  private LedgerAccount getUserCashAccount() {
    return ledgerAccountRepository
        .findByOwnerAndAccountTypeAndAssetType(userParty, ASSET, EUR)
        .orElseThrow();
  }

  private LedgerAccount getUserUnitsAccount() {
    return ledgerAccountService.getLedgerAccount(userParty, ASSET, FUND_UNIT).orElseThrow();
  }

  private LedgerAccount getSystemAccount(
      SystemAccount systemAccount, AssetType assetType, AccountType accountType) {
    return ledgerAccountRepository
        .findByNameAndPurposeAndAssetTypeAndAccountType(
            systemAccount.name(), SYSTEM_ACCOUNT, assetType, accountType)
        .orElseThrow(() -> new RuntimeException("System account not found: " + systemAccount));
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
