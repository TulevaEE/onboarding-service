package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_RECEIVED;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static java.math.BigDecimal.ZERO;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class SavingsFundLedgerTest {

  @Autowired LedgerService ledgerService;
  @Autowired LedgerAccountService ledgerAccountService;
  @Autowired SavingsFundLedger savingsFundLedger;

  User testUser = sampleUser().personalCode("38001010001").build();

  @Test
  void recordPaymentReceived_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("1000.00");
    var externalReference = randomUUID();

    var transaction = savingsFundLedger.recordPaymentReceived(testUser, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RECEIVED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(transaction.getMetadata().get("personalCode")).isEqualTo(testUser.getPersonalCode());
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(amount.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordUnattributedPayment_recordsToUnreconciledAccount() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();

    var transaction = savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("UNATTRIBUTED_PAYMENT");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getUnreconciledBankReceiptsAccount().getBalance())
        .isEqualByComparingTo(amount.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void attributeLatePayment_transfersFromUnreconciledToUser() {
    var amount = new BigDecimal("750.00");
    savingsFundLedger.recordUnattributedPayment(amount, randomUUID());

    var transaction = savingsFundLedger.attributeLatePayment(testUser, amount);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("LATE_ATTRIBUTION");
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(getUnreconciledBankReceiptsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(amount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void bounceBackUnattributedPayment_reversesUnattributedPayment() {
    var amount = new BigDecimal("300.00");
    var externalReference = randomUUID();
    savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    var transaction = savingsFundLedger.bounceBackUnattributedPayment(amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_BOUNCE_BACK");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getUnreconciledBankReceiptsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
    verifyDoubleEntry(transaction);
  }

  @Test
  void bounceBackUnattributedPayment_createsUnattributedRecordWhenMissing() {
    var amount = new BigDecimal("300.00");
    var externalReference = randomUUID();
    // No recordUnattributedPayment call — simulates direct bounce back

    savingsFundLedger.bounceBackUnattributedPayment(amount, externalReference);

    assertThat(getUnreconciledBankReceiptsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  @Test
  void reservePaymentForCancellation_movesCashToReserved() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    savingsFundLedger.recordPaymentReceived(testUser, amount, externalReference);

    var transaction =
        savingsFundLedger.reservePaymentForCancellation(testUser, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType"))
        .isEqualTo("PAYMENT_CANCEL_REQUESTED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(transaction.getMetadata().get("personalCode")).isEqualTo(testUser.getPersonalCode());
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(amount.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordPaymentCancelled_clearsReservedAndBankAsset() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    savingsFundLedger.recordPaymentReceived(testUser, amount, externalReference);
    savingsFundLedger.reservePaymentForCancellation(testUser, amount, externalReference);

    var transaction = savingsFundLedger.recordPaymentCancelled(testUser, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_CANCELLED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(testUser.getId());
    assertThat(transaction.getMetadata().get("personalCode")).isEqualTo(testUser.getPersonalCode());
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordPaymentCancelled_createsReservationWhenMissing() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    savingsFundLedger.recordPaymentReceived(testUser, amount, externalReference);
    // No reservePaymentForCancellation call — simulates manual return

    savingsFundLedger.recordPaymentCancelled(testUser, amount, externalReference);

    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  @Test
  void recordPaymentCancelled_bouncesBackWhenUnattributedPaymentExists() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();

    savingsFundLedger.recordUnattributedPayment(amount, externalReference);
    savingsFundLedger.recordPaymentCancelled(testUser, amount, externalReference);

    assertThat(savingsFundLedger.hasLedgerEntry(externalReference, PAYMENT_RECEIVED)).isFalse();
    assertThat(getUnreconciledBankReceiptsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  @Test
  void recordPaymentCancelled_createsPaymentReceivedWhenMissing() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    // No recordPaymentReceived call — simulates cancellation without prior PAYMENT_RECEIVED

    savingsFundLedger.recordPaymentCancelled(testUser, amount, externalReference);

    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  @Test
  void completeSubscriptionFlow_allBalancesCorrect() {
    var cashAmount = new BigDecimal("1000.00");
    var fundUnits = new BigDecimal("10.00000");
    var navPerUnit = new BigDecimal("100.00");

    var paymentTx = savingsFundLedger.recordPaymentReceived(testUser, cashAmount, randomUUID());
    var reserveTx = savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount);
    var subscriptionTx =
        savingsFundLedger.issueFundUnitsFromReserved(testUser, cashAmount, fundUnits, navPerUnit);
    var transferTx = savingsFundLedger.transferToFundAccount(cashAmount);

    verifyDoubleEntry(paymentTx);
    verifyDoubleEntry(reserveTx);
    verifyDoubleEntry(subscriptionTx);
    verifyDoubleEntry(transferTx);

    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserUnitsAccount().getBalance()).isEqualByComparingTo(fundUnits.negate());
    assertThat(getFundInvestmentCashClearingAccount().getBalance())
        .isEqualByComparingTo(cashAmount);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserSubscriptionsAccount().getBalance())
        .isEqualByComparingTo(cashAmount.negate());
    assertThat(getFundUnitsOutstandingAccount().getBalance()).isEqualByComparingTo(fundUnits);
  }

  @Test
  void completeRedemptionFlow_allBalancesCorrect() {
    var initialAmount = new BigDecimal("1000.00");
    var initialUnits = new BigDecimal("10.00000");
    var redeemUnits = new BigDecimal("3.00000");
    var redeemAmount = new BigDecimal("300.00");
    var navPerUnit = new BigDecimal("100.00");
    var customerIban = "EE777888999000111222";
    setupUserWithFundUnits(initialAmount, initialUnits, navPerUnit);

    var reserveTx = savingsFundLedger.reserveFundUnitsForRedemption(testUser, redeemUnits);
    var redemptionTx =
        savingsFundLedger.redeemFundUnitsFromReserved(
            testUser, redeemUnits, redeemAmount, navPerUnit);
    var cashTransferTx = savingsFundLedger.transferFromFundAccount(redeemAmount);
    var payoutTx = savingsFundLedger.recordRedemptionPayout(testUser, redeemAmount, customerIban);

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
  void hasLedgerEntry_detectsEntriesByExternalReference() {
    var amount = new BigDecimal("500.00");
    var paymentRef = randomUUID();
    var bounceBackRef = randomUUID();
    var receivedRef = randomUUID();

    assertThat(savingsFundLedger.hasLedgerEntry(paymentRef)).isFalse();

    savingsFundLedger.recordUnattributedPayment(amount, paymentRef);
    assertThat(savingsFundLedger.hasLedgerEntry(paymentRef)).isTrue();

    savingsFundLedger.bounceBackUnattributedPayment(amount, bounceBackRef);
    assertThat(savingsFundLedger.hasLedgerEntry(bounceBackRef)).isTrue();

    savingsFundLedger.recordPaymentReceived(testUser, amount, receivedRef);
    assertThat(savingsFundLedger.hasLedgerEntry(receivedRef)).isTrue();

    assertThat(savingsFundLedger.hasLedgerEntry(randomUUID())).isFalse();
  }

  @Test
  void recordPaymentReceived_autoCreatesPartyAndAccountsForNewUser() {
    var newUser = sampleUser().personalCode("99999999999").build();
    var amount = new BigDecimal("100.00");
    var externalReference = randomUUID();

    var transaction = savingsFundLedger.recordPaymentReceived(newUser, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RECEIVED");
    assertThat(transaction.getMetadata().get("userId")).isEqualTo(newUser.getId());
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    verifyDoubleEntry(transaction);
  }

  @Test
  void subscribeAndRedeemRoundTrip_allBalancesReturnToZero() {
    var cashAmount = new BigDecimal("1000.00");
    var fundUnits = new BigDecimal("10.00000");
    var navPerUnit = new BigDecimal("100.00");
    var customerIban = "EE123456789012345678";

    savingsFundLedger.recordPaymentReceived(testUser, cashAmount, randomUUID());
    savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount);
    savingsFundLedger.issueFundUnitsFromReserved(testUser, cashAmount, fundUnits, navPerUnit);
    savingsFundLedger.transferToFundAccount(cashAmount);

    assertThat(getUserUnitsAccount().getBalance()).isEqualByComparingTo(fundUnits.negate());
    assertThat(getFundUnitsOutstandingAccount().getBalance()).isEqualByComparingTo(fundUnits);
    assertThat(getUserSubscriptionsAccount().getBalance())
        .isEqualByComparingTo(cashAmount.negate());

    savingsFundLedger.reserveFundUnitsForRedemption(testUser, fundUnits);
    savingsFundLedger.redeemFundUnitsFromReserved(testUser, fundUnits, cashAmount, navPerUnit);
    savingsFundLedger.transferFromFundAccount(cashAmount);
    savingsFundLedger.recordRedemptionPayout(testUser, cashAmount, customerIban);

    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashRedemptionAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserUnitsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserReservedUnitsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getFundUnitsOutstandingAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getFundInvestmentCashClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getPayoutsCashClearingAccount().getBalance()).isEqualByComparingTo(ZERO);

    assertThat(getUserSubscriptionsAccount().getBalance())
        .isEqualByComparingTo(cashAmount.negate());
    assertThat(getUserRedemptionsAccount().getBalance()).isEqualByComparingTo(cashAmount);
  }

  @Test
  void recordBankFee_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("-1.50");
    var externalReference = randomUUID();

    var transaction =
        savingsFundLedger.recordBankFee(amount, externalReference, INCOMING_PAYMENTS_CLEARING);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("BANK_FEE");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getSystemAccount(BANK_FEE).getBalance()).isEqualByComparingTo(amount.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordInterestReceived_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("5.00");
    var externalReference = randomUUID();

    var transaction =
        savingsFundLedger.recordInterestReceived(
            amount, externalReference, INCOMING_PAYMENTS_CLEARING);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("INTEREST_RECEIVED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(amount);
    assertThat(getSystemAccount(INTEREST_INCOME).getBalance())
        .isEqualByComparingTo(amount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordAdjustment_systemToSystem_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("50.00");

    var transaction =
        savingsFundLedger.recordAdjustment(
            "INCOMING_PAYMENTS_CLEARING",
            null,
            "BANK_ADJUSTMENT",
            null,
            amount,
            null,
            "Test adjustment");

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("ADJUSTMENT");
    assertThat(transaction.getMetadata().get("description")).isEqualTo("Test adjustment");
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(amount);
    assertThat(getSystemAccount(BANK_ADJUSTMENT).getBalance())
        .isEqualByComparingTo(amount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordAdjustment_userToSystem_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("25.00");
    savingsFundLedger.recordPaymentReceived(testUser, amount, randomUUID());

    var transaction =
        savingsFundLedger.recordAdjustment(
            "CASH",
            testUser.getPersonalCode(),
            "INCOMING_PAYMENTS_CLEARING",
            null,
            amount,
            null,
            "User to system adjustment");

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("ADJUSTMENT");
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordAdjustment_differentUsersToUser_throwsException() {
    savingsFundLedger.recordPaymentReceived(testUser, new BigDecimal("100.00"), randomUUID());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            savingsFundLedger.recordAdjustment(
                "CASH",
                "38001010001",
                "CASH",
                "38001010002",
                new BigDecimal("10.00"),
                null,
                "Invalid cross-user"));
  }

  @Test
  void recordAdjustment_sameUserDifferentAccounts_succeeds() {
    savingsFundLedger.recordPaymentReceived(testUser, new BigDecimal("100.00"), randomUUID());
    savingsFundLedger.reservePaymentForSubscription(testUser, new BigDecimal("100.00"));

    var transaction =
        savingsFundLedger.recordAdjustment(
            "CASH_RESERVED",
            "38001010001",
            "CASH",
            "38001010001",
            new BigDecimal("10.00"),
            null,
            "Reverse duplicate reservation");

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("ADJUSTMENT");
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordBankAdjustment_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("0.500");
    var externalReference = randomUUID();

    var transaction =
        savingsFundLedger.recordBankAdjustment(
            amount, externalReference, INCOMING_PAYMENTS_CLEARING);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("BANK_ADJUSTMENT");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getSystemAccount(BANK_ADJUSTMENT).getBalance())
        .isEqualByComparingTo(amount.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordTradeSettlement_createsCorrectDoubleEntry() {
    var amount = new BigDecimal("-209025.86");
    var externalReference = randomUUID();
    var isin = "LU1291102447";

    var transaction =
        savingsFundLedger.recordTradeSettlement(
            amount,
            externalReference,
            FUND_INVESTMENT_CASH_CLEARING,
            isin,
            "EJAP",
            "BNP Paribas Easy MSCI Japan ESG Filtered");

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("TRADE_SETTLEMENT");
    assertThat(transaction.getMetadata().get("instrument")).isEqualTo(isin);
    assertThat(transaction.getMetadata().get("ticker")).isEqualTo("EJAP");
    assertThat(transaction.getMetadata().get("displayName"))
        .isEqualTo("BNP Paribas Easy MSCI Japan ESG Filtered");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getFundInvestmentCashClearingAccount().getBalance()).isEqualByComparingTo(amount);
    assertThat(getTradeSettlementAccount(isin).getBalance()).isEqualByComparingTo(amount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordTradeSettlement_createsPerInstrumentAccounts() {
    var amount1 = new BigDecimal("-209025.86");
    var amount2 = new BigDecimal("-995467.50");
    var isin1 = "LU1291102447";
    var isin2 = "IE00BJZ2DC62";

    savingsFundLedger.recordTradeSettlement(
        amount1, randomUUID(), FUND_INVESTMENT_CASH_CLEARING, isin1, "EJAP", "BNP Japan");
    savingsFundLedger.recordTradeSettlement(
        amount2, randomUUID(), FUND_INVESTMENT_CASH_CLEARING, isin2, "XRSM", "Xtrackers USA");

    assertThat(getTradeSettlementAccount(isin1).getBalance())
        .isEqualByComparingTo(amount1.negate());
    assertThat(getTradeSettlementAccount(isin2).getBalance())
        .isEqualByComparingTo(amount2.negate());
  }

  private void setupUserWithFundUnits(
      BigDecimal cashAmount, BigDecimal fundUnits, BigDecimal navPerUnit) {
    savingsFundLedger.recordPaymentReceived(testUser, cashAmount, randomUUID());
    savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount);
    savingsFundLedger.issueFundUnitsFromReserved(testUser, cashAmount, fundUnits, navPerUnit);
    savingsFundLedger.transferToFundAccount(cashAmount);
  }

  private LedgerAccount getUserAccount(UserAccount userAccount) {
    return ledgerService.getUserAccount(testUser, userAccount);
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerService.getSystemAccount(systemAccount);
  }

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

  private LedgerAccount getTradeSettlementAccount(String isin) {
    return ledgerAccountService
        .findSystemAccountByName("TRADE_SETTLEMENT:" + isin, ASSET, EUR)
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
