package ee.tuleva.onboarding.ledger

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*
import static java.math.BigDecimal.ZERO
import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

import ee.tuleva.onboarding.ledger.*
import ee.tuleva.onboarding.user.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class SavingsFundLedgerServiceSpec {

  @Autowired
  SavingsFundLedgerService savingsFundLedgerService

  @Autowired
  LedgerService ledgerService

  @Autowired
  LedgerAccountService ledgerAccountService

  @Autowired
  LedgerPartyService ledgerPartyService

  @Autowired
  LedgerAccountRepository ledgerAccountRepository

  @Autowired
  LedgerPartyRepository ledgerPartyRepository

  @Autowired
  LedgerTransactionRepository ledgerTransactionRepository

  @Autowired
  EntityManager entityManager

  User testUser
  LedgerParty userParty

  @BeforeEach
  void setUp() {
    testUser = sampleUser().personalCode("38001010001").build()
    ledgerService.onboardUser(testUser)
    userParty = ledgerPartyService.getPartyForUser(testUser).orElseThrow()
  }

  @AfterEach
  void cleanup() {
    ledgerTransactionRepository.deleteAll()
    ledgerAccountRepository.deleteAll()
    ledgerPartyRepository.deleteAll()
  }

  @Test
  @DisplayName("Money-in flow: Payment received should create correct ledger entries")
  void shouldRecordPaymentReceived() {
    def amount = 1000.00
    def externalRef = "MONTONIO_123456"

    def transaction = savingsFundLedgerService.recordPaymentReceived(testUser, amount, externalRef)

    assertThat(transaction).isNotNull()
    assertThat(transaction.metadata.get("operationType")).isEqualTo("PAYMENT_RECEIVED")
    assertThat(transaction.metadata.get("externalReference")).isEqualTo(externalRef)
    assertThat(transaction.metadata.get("userId")).isEqualTo(testUser.id)
    assertThat(transaction.metadata.get("personalCode")).isEqualTo(testUser.personalCode)

    // Verify user cash account balance increased
    assertThat(getUserCashAccount().balance).isEqualByComparingTo(amount)

    // Verify incoming payments clearing liability increased
    assertThat(getSystemAccount("INCOMING_PAYMENTS_CLEARING", EUR, LIABILITY).balance).isEqualByComparingTo(amount.negate())

    // Verify double-entry accounting is maintained
    verifyDoubleEntry(transaction)
  }

  @Test
  @DisplayName("Reconciliation flow: Unattributed payment should be recorded separately")
  void testRecordUnattributedPayment() {
    def amount = 500.00
    def payerIban = "EE123456789012345678"
    def externalRef = "UNATTRIBUTED_789"

    def transaction = savingsFundLedgerService.recordUnattributedPayment(amount, payerIban, externalRef)

    assertThat(transaction).isNotNull()
    assertThat(transaction.metadata.get("operationType")).isEqualTo("UNATTRIBUTED_PAYMENT")
    assertThat(transaction.metadata.get("payerIban")).isEqualTo(payerIban)
    assertThat(transaction.metadata.get("externalReference")).isEqualTo(externalRef)

    assertThat(getSystemAccount("UNRECONCILED_BANK_RECEIPTS", EUR, ASSET).balance).isEqualByComparingTo(amount)
    assertThat(getSystemAccount("INCOMING_PAYMENTS_CLEARING", EUR, LIABILITY).balance).isEqualByComparingTo(amount.negate())

    verifyDoubleEntry(transaction)
  }

  @Test
  @DisplayName("Reconciliation flow: Late attribution should transfer from unreconciled to user")
  void testAttributeLatePayment() {
    def amount = 750.00
    def payerIban = "EE987654321098765432"
    savingsFundLedgerService.recordUnattributedPayment(amount, payerIban, "LATE_REF")

    def transaction = savingsFundLedgerService.attributeLatePayment(testUser, amount)

    assertThat(transaction).isNotNull()
    assertThat(transaction.metadata.get("operationType")).isEqualTo("LATE_ATTRIBUTION")
    assertThat(transaction.metadata.get("userId")).isEqualTo(testUser.id)
    assertThat(transaction.metadata.get("personalCode")).isEqualTo(testUser.personalCode)

    assertThat(getSystemAccount("UNRECONCILED_BANK_RECEIPTS", EUR, ASSET).balance).isEqualByComparingTo(ZERO)
    assertThat(getUserCashAccount().balance).isEqualByComparingTo(amount)

    verifyDoubleEntry(transaction)
  }

  @Test
  @DisplayName("Reconciliation flow: Bounce-back should reverse unattributed payment")
  void testBounceBackUnattributedPayment() {
    def amount = 300.00
    def payerIban = "EE555666777888999000"
    savingsFundLedgerService.recordUnattributedPayment(amount, payerIban, "BOUNCE_REF")

    def transaction = savingsFundLedgerService.bounceBackUnattributedPayment(amount, payerIban)

    assertThat(transaction).isNotNull()
    assertThat(transaction.metadata.get("operationType")).isEqualTo("PAYMENT_BOUNCE_BACK")
    assertThat(transaction.metadata.get("payerIban")).isEqualTo(payerIban)

    assertThat(getSystemAccount("UNRECONCILED_BANK_RECEIPTS", EUR, ASSET).balance).isEqualByComparingTo(ZERO)
    assertThat(getSystemAccount("INCOMING_PAYMENTS_CLEARING", EUR, LIABILITY).balance).isEqualByComparingTo(ZERO)

    verifyDoubleEntry(transaction)
  }

  @Test
  @DisplayName("Subscription flow: Should issue fund units for user cash")
  void testIssueFundUnits() {
    def cashAmount = 950.00
    def fundUnits = 10.0000
    def navPerUnit = 95.00
    savingsFundLedgerService.recordPaymentReceived(testUser, cashAmount, "SETUP_REF")

    def transaction = savingsFundLedgerService.issueFundUnits(testUser, cashAmount, fundUnits, navPerUnit)

    assertThat(transaction).isNotNull()
    assertThat(transaction.metadata.get("operationType")).isEqualTo("FUND_SUBSCRIPTION")
    assertThat(transaction.metadata.get("navPerUnit")).isEqualTo(navPerUnit)
    assertThat(transaction.metadata.get("userId")).isEqualTo(testUser.id)

    assertThat(getUserCashAccount().balance).isEqualByComparingTo(ZERO)
    assertThat(getUserUnitsAccount().balance).isEqualByComparingTo(fundUnits)
    assertThat(getSystemAccount("FUND_SUBSCRIPTIONS_PAYABLE", EUR, LIABILITY).balance).isEqualByComparingTo(cashAmount)
    assertThat(getSystemAccount("FUND_UNITS_OUTSTANDING", FUND_UNIT, LIABILITY).balance).isEqualByComparingTo(fundUnits.negate())

    verifyDoubleEntry(transaction)
  }

  @Test
  @DisplayName("Subscription flow: Should transfer cash to fund investment account")
  void testTransferToFundAccount() {
    def amount = 2000.00
    savingsFundLedgerService.recordPaymentReceived(testUser, amount, "FUND_TRANSFER_REF")

    def transaction = savingsFundLedgerService.transferToFundAccount(amount)

    // Force flush to ensure all changes are persisted
    entityManager.flush()
    entityManager.clear() // Clear the persistence context to force fresh loads

    assertThat(transaction).isNotNull()
    assertThat(transaction.metadata.get("operationType")).isEqualTo("FUND_TRANSFER")

    def incomingAccount = getSystemAccount("INCOMING_PAYMENTS_CLEARING", EUR, LIABILITY)
    assertThat(incomingAccount.balance).isEqualByComparingTo(ZERO)

    def fundAccount = getSystemAccount("FUND_INVESTMENT_CASH_CLEARING", EUR, ASSET)
    assertThat(fundAccount.balance).isEqualByComparingTo(amount.negate())

    verifyDoubleEntry(transaction)
  }

  @Test
  @DisplayName("Redemption flow: Should process redemption request correctly")
  void testProcessRedemption() {
    def initialCash = 1000.00
    def initialUnits = 10.0000
    def redeemUnits = 4.0000
    def redeemAmount = 400.00
    def navPerUnit = 100.00
    setupUserWithFundUnits(initialCash, initialUnits, navPerUnit)

    def transaction = savingsFundLedgerService.processRedemption(testUser, redeemUnits, redeemAmount, navPerUnit)

    assertThat(transaction).isNotNull()
    assertThat(transaction.metadata.get("operationType")).isEqualTo("REDEMPTION_REQUEST")
    assertThat(transaction.metadata.get("navPerUnit")).isEqualTo(navPerUnit)
    assertThat(transaction.metadata.get("userId")).isEqualTo(testUser.id)

    assertThat(getUserUnitsAccount().balance).isEqualByComparingTo(initialUnits.subtract(redeemUnits))
    assertThat(getSystemAccount("FUND_UNITS_OUTSTANDING", FUND_UNIT, LIABILITY).balance).isEqualByComparingTo(initialUnits.negate().add(redeemUnits))
    assertThat(getSystemAccount("REDEMPTION_PAYABLE", EUR, LIABILITY).balance).isEqualByComparingTo(redeemAmount)
    assertThat(getSystemAccount("FUND_INVESTMENT_CASH_CLEARING", EUR, ASSET).balance).isEqualByComparingTo(initialCash.negate().subtract(redeemAmount))

    verifyDoubleEntry(transaction)
  }

  @Test
  @DisplayName("Redemption flow: Should transfer fund cash to payout clearing")
  void testTransferFundToPayoutCash() {
    def amount = 1200.00
    setupFundWithCash(amount)

    def transaction = savingsFundLedgerService.transferFundToPayoutCash(amount)

    assertThat(transaction).isNotNull()
    assertThat(transaction.metadata.get("operationType")).isEqualTo("FUND_CASH_TRANSFER")

    assertThat(getSystemAccount("FUND_INVESTMENT_CASH_CLEARING", EUR, ASSET).balance).isEqualByComparingTo(ZERO)
    assertThat(getSystemAccount("PAYOUTS_CASH_CLEARING", EUR, ASSET).balance).isEqualByComparingTo(amount.negate())

    verifyDoubleEntry(transaction)
  }

  @Test
  @DisplayName("Redemption flow: Should process final payout to customer")
  void testProcessRedemptionPayout() {
    def amount = 500.00
    def customerIban = "EE111222333444555666"
    setupRedemptionScenario(amount)

    def transaction = savingsFundLedgerService.processRedemptionPayout(testUser, amount, customerIban)

    assertThat(transaction).isNotNull()
    assertThat(transaction.metadata.get("operationType")).isEqualTo("REDEMPTION_PAYOUT")
    assertThat(transaction.metadata.get("customerIban")).isEqualTo(customerIban)
    assertThat(transaction.metadata.get("userId")).isEqualTo(testUser.id)

    assertThat(getSystemAccount("PAYOUTS_CASH_CLEARING", EUR, ASSET).balance).isEqualByComparingTo(ZERO)
    assertThat(getSystemAccount("REDEMPTION_PAYABLE", EUR, LIABILITY).balance).isEqualByComparingTo(ZERO)

    verifyDoubleEntry(transaction)
  }

  @Test
  @DisplayName("Complete subscription flow: Payment → Units → Fund transfer")
  void testCompleteSubscriptionFlow() {
    def paymentAmount = 1000.00
    def fundUnits = 10.5263
    def navPerUnit = 95.00

    def paymentTx = savingsFundLedgerService.recordPaymentReceived(testUser, paymentAmount, "COMPLETE_FLOW_REF")
    def subscriptionTx = savingsFundLedgerService.issueFundUnits(testUser, paymentAmount, fundUnits, navPerUnit)
    def transferTx = savingsFundLedgerService.transferToFundAccount(paymentAmount)

    verifyDoubleEntry(paymentTx)
    verifyDoubleEntry(subscriptionTx)
    verifyDoubleEntry(transferTx)

    assertThat(getUserCashAccount().balance).isEqualByComparingTo(ZERO)
    assertThat(getUserUnitsAccount().balance).isEqualByComparingTo(fundUnits)
    assertThat(getSystemAccount("FUND_INVESTMENT_CASH_CLEARING", EUR, ASSET).balance).isEqualByComparingTo(paymentAmount.negate())
    assertThat(getSystemAccount("INCOMING_PAYMENTS_CLEARING", EUR, LIABILITY).balance).isEqualByComparingTo(ZERO)
  }

  @Test
  @DisplayName("Complete redemption flow: Request → Cash transfer → Payout")
  void testCompleteRedemptionFlow() {
    def initialAmount = 1000.00
    def initialUnits = 10.0000
    def redeemUnits = 3.0000
    def redeemAmount = 300.00
    def navPerUnit = 100.00
    def customerIban = "EE777888999000111222"
    setupUserWithFundUnits(initialAmount, initialUnits, navPerUnit)

    def redemptionTx = savingsFundLedgerService.processRedemption(testUser, redeemUnits, redeemAmount, navPerUnit)
    def cashTransferTx = savingsFundLedgerService.transferFundToPayoutCash(redeemAmount)
    def payoutTx = savingsFundLedgerService.processRedemptionPayout(testUser, redeemAmount, customerIban)

    verifyDoubleEntry(redemptionTx)
    verifyDoubleEntry(cashTransferTx)
    verifyDoubleEntry(payoutTx)

    assertThat(getUserUnitsAccount().balance).isEqualByComparingTo(initialUnits.subtract(redeemUnits))
    assertThat(getSystemAccount("REDEMPTION_PAYABLE", EUR, LIABILITY).balance).isEqualByComparingTo(ZERO)
    assertThat(getSystemAccount("PAYOUTS_CASH_CLEARING", EUR, ASSET).balance).isEqualByComparingTo(ZERO)
  }

  @Test
  @DisplayName("Should throw exception for user not onboarded")
  void testThrowExceptionForUnonboardedUser() {
    def unonboardedUser = sampleUser().personalCode("99999999999").build()

    assertThatThrownBy({
      savingsFundLedgerService.recordPaymentReceived(unonboardedUser, BigDecimal.TEN, "REF")
    }).isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("User not onboarded")
  }

  @Test
  @DisplayName("Should maintain accounting relationships after complex operations")
  void testMaintainAccountingRelationshipsAfterComplexOperations() {
    def amount = 1000.00

    def payment = savingsFundLedgerService.recordPaymentReceived(testUser, amount, "BALANCE_TEST")
    def subscription = savingsFundLedgerService.issueFundUnits(testUser, amount, 10.0, 100.00)
    def transfer = savingsFundLedgerService.transferToFundAccount(amount)

    verifyDoubleEntry(payment)
    verifyDoubleEntry(subscription)
    verifyDoubleEntry(transfer)

    def userCashAccount = getUserCashAccount()
    assertThat(userCashAccount.entries).isNotNull()
    assertThat(userCashAccount.balance).isEqualByComparingTo(ZERO)

    def userUnitsAccount = getUserUnitsAccount()
    assertThat(userUnitsAccount.entries).isNotNull()
    assertThat(userUnitsAccount.balance).isEqualByComparingTo(10.0)

    def fundSubscriptionsAccount = getSystemAccount("FUND_SUBSCRIPTIONS_PAYABLE", EUR, LIABILITY)
    assertThat(fundSubscriptionsAccount.entries).isNotNull()
    assertThat(fundSubscriptionsAccount.balance).isEqualByComparingTo(amount)

    def fundInvestmentAccount = getSystemAccount("FUND_INVESTMENT_CASH_CLEARING", EUR, ASSET)
    assertThat(fundInvestmentAccount.entries).isNotNull()
    assertThat(fundInvestmentAccount.balance).isEqualByComparingTo(amount.negate())

    def incomingPaymentsAccount = getSystemAccount("INCOMING_PAYMENTS_CLEARING", EUR, LIABILITY)
    assertThat(incomingPaymentsAccount.entries).isNotNull()
    assertThat(incomingPaymentsAccount.balance).isEqualByComparingTo(ZERO)
  }

  // Helper methods

  private void setupUserWithFundUnits(BigDecimal cashAmount, BigDecimal fundUnits, BigDecimal navPerUnit) {
    savingsFundLedgerService.recordPaymentReceived(testUser, cashAmount, "SETUP_PAYMENT")
    savingsFundLedgerService.issueFundUnits(testUser, cashAmount, fundUnits, navPerUnit)
    savingsFundLedgerService.transferToFundAccount(cashAmount)
  }

  private void setupFundWithCash(BigDecimal amount) {
    savingsFundLedgerService.recordPaymentReceived(testUser, amount, "FUND_SETUP")
    savingsFundLedgerService.transferToFundAccount(amount)
  }

  private void setupRedemptionScenario(BigDecimal amount) {
    setupUserWithFundUnits(amount, 5.0, 100.00)
    savingsFundLedgerService.processRedemption(testUser, 5.0, amount, 100.00)
    savingsFundLedgerService.transferFundToPayoutCash(amount)
  }

  private LedgerAccount getUserCashAccount() {
    return ledgerAccountRepository.findByOwnerAndAccountTypeAndAssetType(userParty, INCOME, EUR)
  }

  private LedgerAccount getUserUnitsAccount() {
    return ledgerAccountService.getLedgerAccount(userParty, ASSET, FUND_UNIT).orElseThrow()
  }

  private LedgerAccount getSystemAccount(String name, LedgerAccount.AssetType assetType, LedgerAccount.AccountType accountType) {
    return ledgerAccountRepository.findByNameAndPurposeAndAssetTypeAndAccountType(name, SYSTEM_ACCOUNT, assetType, accountType)
        .orElseThrow { new RuntimeException("System account not found: $name") }
  }

  private void verifyDoubleEntry(LedgerTransaction transaction) {
    def entries = transaction.entries

    assert entries.size() > 1

    def totalDebits = entries.stream()
        .filter { (it.amount > ZERO) }
        .map { it.amount }
        .reduce(ZERO) { accumulator, amount -> accumulator + amount }

    def totalCredits = entries.stream()
        .filter { (it.amount < ZERO) }
        .map { it.amount.abs() }
        .reduce(ZERO) { accumulator, amount -> accumulator + amount }

    assert totalDebits.compareTo(totalCredits) == 0
  }
}
