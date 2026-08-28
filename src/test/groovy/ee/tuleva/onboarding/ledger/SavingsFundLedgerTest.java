package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_BOUNCE_BACK;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_RECEIVED;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static java.math.BigDecimal.ZERO;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  LedgerService.class,
  LedgerAccountService.class,
  LedgerPartyService.class,
  LedgerTransactionService.class,
  SavingsFundLedger.class,
  ClockConfig.class
})
class SavingsFundLedgerTest {

  @Autowired LedgerService ledgerService;
  @Autowired LedgerAccountService ledgerAccountService;
  @Autowired SavingsFundLedger savingsFundLedger;

  PartyId testParty = new PartyId(PartyId.Type.PERSON, "38001010001");

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void systemAccounts_areFundQualified() {
    savingsFundLedger.recordPaymentReceived(testParty, new BigDecimal("100.00"), randomUUID());

    assertThat(getIncomingPaymentsClearingAccount().getName())
        .isEqualTo("INCOMING_PAYMENTS_CLEARING:TKF100");
    assertThat(getFundUnitsOutstandingAccount().getName())
        .isEqualTo("FUND_UNITS_OUTSTANDING:TKF100");
  }

  @Test
  void recordPaymentReceived_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("1000.00");
    var externalReference = randomUUID();
    var userCashBefore = getUserCashAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();

    var transaction = savingsFundLedger.recordPaymentReceived(testParty, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RECEIVED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("partyCode")).isEqualTo(testParty.code());
    assertThat(transaction.getMetadata().get("partyType")).isEqualTo("PERSON");
    assertThat(deltaSince(userCashBefore, getUserCashAccount()))
        .isEqualByComparingTo(amount.negate());
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordUnattributedPayment_recordsToUnreconciledAccount() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    var unreconciledBefore = getUnreconciledBankReceiptsAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();

    var transaction = savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("UNATTRIBUTED_PAYMENT");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(deltaSince(unreconciledBefore, getUnreconciledBankReceiptsAccount()))
        .isEqualByComparingTo(amount.negate());
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void reconcileUnattributedPayment_creditsPartyAndClearsParking() {
    var amount = new BigDecimal("1000.00");
    var externalReference = randomUUID();
    var unreconciledBefore = getUnreconciledBankReceiptsAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    var userCashBefore = getUserCashAccount().getBalance();
    savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    var transaction =
        savingsFundLedger.reconcileUnattributedPayment(testParty, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType"))
        .isEqualTo("UNATTRIBUTED_PAYMENT_RECONCILED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("partyCode")).isEqualTo(testParty.code());
    assertThat(transaction.getMetadata().get("partyType")).isEqualTo("PERSON");
    assertThat(deltaSince(unreconciledBefore, getUnreconciledBankReceiptsAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(amount);
    assertThat(deltaSince(userCashBefore, getUserCashAccount()))
        .isEqualByComparingTo(amount.negate());
    assertThat(savingsFundLedger.hasLedgerEntry(externalReference, PAYMENT_RECEIVED)).isTrue();
    verifyDoubleEntry(transaction);
  }

  @Test
  void reconcileUnattributedPayment_isIdempotent() {
    var amount = new BigDecimal("1000.00");
    var externalReference = randomUUID();
    var unreconciledBefore = getUnreconciledBankReceiptsAccount().getBalance();
    var userCashBefore = getUserCashAccount().getBalance();
    savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    var first =
        savingsFundLedger.reconcileUnattributedPayment(testParty, amount, externalReference);
    var second =
        savingsFundLedger.reconcileUnattributedPayment(testParty, amount, externalReference);

    assertThat(second).isEqualTo(first);
    assertThat(deltaSince(unreconciledBefore, getUnreconciledBankReceiptsAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashBefore, getUserCashAccount()))
        .isEqualByComparingTo(amount.negate());
  }

  @Test
  void reconcileUnattributedPayment_withBookingDate_usesBookingDate() {
    var amount = new BigDecimal("1000.00");
    var externalReference = randomUUID();
    var bookingDate = LocalDate.of(2026, 6, 12);
    savingsFundLedger.recordUnattributedPayment(amount, externalReference, bookingDate);

    var transaction =
        savingsFundLedger.reconcileUnattributedPayment(
            testParty, amount, externalReference, bookingDate);

    assertThat(transaction.getTransactionDate().atZone(ZoneId.of("Europe/Tallinn")).toLocalDate())
        .isEqualTo(bookingDate);
  }

  @Test
  void recordPaymentCancelled_afterReconciliation_doesNotBounceBack() {
    var amount = new BigDecimal("1000.00");
    var externalReference = randomUUID();
    var unreconciledBefore = getUnreconciledBankReceiptsAccount().getBalance();
    var userCashBefore = getUserCashAccount().getBalance();
    var userCashReservedBefore = getUserCashReservedAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    savingsFundLedger.recordUnattributedPayment(amount, externalReference);
    savingsFundLedger.reconcileUnattributedPayment(testParty, amount, externalReference);

    savingsFundLedger.recordPaymentCancelled(testParty, amount, externalReference);

    assertThat(savingsFundLedger.hasLedgerEntry(externalReference, PAYMENT_BOUNCE_BACK)).isFalse();
    assertThat(deltaSince(unreconciledBefore, getUnreconciledBankReceiptsAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashBefore, getUserCashAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashReservedBefore, getUserCashReservedAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
  }

  @Test
  void bounceBackUnattributedPayment_reversesUnattributedPayment() {
    var amount = new BigDecimal("300.00");
    var externalReference = randomUUID();
    var unreconciledBefore = getUnreconciledBankReceiptsAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    var transaction = savingsFundLedger.bounceBackUnattributedPayment(amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_BOUNCE_BACK");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(deltaSince(unreconciledBefore, getUnreconciledBankReceiptsAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
    verifyDoubleEntry(transaction);
  }

  @Test
  void bounceBackUnattributedPayment_createsUnattributedRecordWhenMissing() {
    var amount = new BigDecimal("300.00");
    var externalReference = randomUUID();
    var unreconciledBefore = getUnreconciledBankReceiptsAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    // No recordUnattributedPayment call — simulates direct bounce back

    savingsFundLedger.bounceBackUnattributedPayment(amount, externalReference);

    assertThat(deltaSince(unreconciledBefore, getUnreconciledBankReceiptsAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
  }

  @Test
  void reservePaymentForCancellation_movesCashToReserved() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    var userCashBefore = getUserCashAccount().getBalance();
    var userCashReservedBefore = getUserCashReservedAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    savingsFundLedger.recordPaymentReceived(testParty, amount, externalReference);

    var transaction =
        savingsFundLedger.reservePaymentForCancellation(testParty, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType"))
        .isEqualTo("PAYMENT_CANCEL_REQUESTED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("partyCode")).isEqualTo(testParty.code());
    assertThat(transaction.getMetadata().get("partyType")).isEqualTo("PERSON");
    assertThat(deltaSince(userCashBefore, getUserCashAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashReservedBefore, getUserCashReservedAccount()))
        .isEqualByComparingTo(amount.negate());
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordPaymentCancelled_clearsReservedAndBankAsset() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    var userCashBefore = getUserCashAccount().getBalance();
    var userCashReservedBefore = getUserCashReservedAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    savingsFundLedger.recordPaymentReceived(testParty, amount, externalReference);
    savingsFundLedger.reservePaymentForCancellation(testParty, amount, externalReference);

    var transaction =
        savingsFundLedger.recordPaymentCancelled(testParty, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_CANCELLED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("partyCode")).isEqualTo(testParty.code());
    assertThat(transaction.getMetadata().get("partyType")).isEqualTo("PERSON");
    assertThat(deltaSince(userCashBefore, getUserCashAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashReservedBefore, getUserCashReservedAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordPaymentCancelled_createsReservationWhenMissing() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    var userCashBefore = getUserCashAccount().getBalance();
    var userCashReservedBefore = getUserCashReservedAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    savingsFundLedger.recordPaymentReceived(testParty, amount, externalReference);
    // No reservePaymentForCancellation call — simulates manual return

    savingsFundLedger.recordPaymentCancelled(testParty, amount, externalReference);

    assertThat(deltaSince(userCashBefore, getUserCashAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashReservedBefore, getUserCashReservedAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
  }

  @Test
  void recordPaymentCancelled_bouncesBackWhenUnattributedPaymentExists() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    var unreconciledBefore = getUnreconciledBankReceiptsAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    var userCashBefore = getUserCashAccount().getBalance();
    var userCashReservedBefore = getUserCashReservedAccount().getBalance();

    savingsFundLedger.recordUnattributedPayment(amount, externalReference);
    savingsFundLedger.recordPaymentCancelled(testParty, amount, externalReference);

    assertThat(savingsFundLedger.hasLedgerEntry(externalReference, PAYMENT_RECEIVED)).isFalse();
    assertThat(deltaSince(unreconciledBefore, getUnreconciledBankReceiptsAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashBefore, getUserCashAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashReservedBefore, getUserCashReservedAccount()))
        .isEqualByComparingTo(ZERO);
  }

  @Test
  void recordPaymentCancelled_createsPaymentReceivedWhenMissing() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    var userCashBefore = getUserCashAccount().getBalance();
    var userCashReservedBefore = getUserCashReservedAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    // No recordPaymentReceived call — simulates cancellation without prior PAYMENT_RECEIVED

    savingsFundLedger.recordPaymentCancelled(testParty, amount, externalReference);

    assertThat(deltaSince(userCashBefore, getUserCashAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashReservedBefore, getUserCashReservedAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
  }

  @Test
  void completeSubscriptionFlow_allBalancesCorrect() {
    var cashAmount = new BigDecimal("1000.00");
    var fundUnits = new BigDecimal("10.00000");
    var navPerUnit = new BigDecimal("100.00");
    var paymentId = randomUUID();
    var userCashBefore = getUserCashAccount().getBalance();
    var userCashReservedBefore = getUserCashReservedAccount().getBalance();
    var userUnitsBefore = getUserUnitsAccount().getBalance();
    var fundInvestmentCashClearingBefore = getFundInvestmentCashClearingAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    var userSubscriptionsBefore = getUserSubscriptionsAccount().getBalance();
    var fundUnitsOutstandingBefore = getFundUnitsOutstandingAccount().getBalance();

    var paymentTx = savingsFundLedger.recordPaymentReceived(testParty, cashAmount, paymentId);
    var reserveTx =
        savingsFundLedger.reservePaymentForSubscription(testParty, cashAmount, paymentId);
    var subscriptionTx =
        savingsFundLedger.issueFundUnitsFromReserved(
            testParty, cashAmount, fundUnits, navPerUnit, paymentId);
    var transferTx = savingsFundLedger.transferToFundAccount(cashAmount, paymentId);

    verifyDoubleEntry(paymentTx);
    verifyDoubleEntry(reserveTx);
    verifyDoubleEntry(subscriptionTx);
    verifyDoubleEntry(transferTx);

    assertThat(deltaSince(userCashBefore, getUserCashAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashReservedBefore, getUserCashReservedAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userUnitsBefore, getUserUnitsAccount()))
        .isEqualByComparingTo(fundUnits.negate());
    assertThat(deltaSince(fundInvestmentCashClearingBefore, getFundInvestmentCashClearingAccount()))
        .isEqualByComparingTo(cashAmount);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userSubscriptionsBefore, getUserSubscriptionsAccount()))
        .isEqualByComparingTo(cashAmount.negate());
    assertThat(deltaSince(fundUnitsOutstandingBefore, getFundUnitsOutstandingAccount()))
        .isEqualByComparingTo(fundUnits);
  }

  @Test
  void completeRedemptionFlow_allBalancesCorrect() {
    var initialAmount = new BigDecimal("1000.00");
    var initialUnits = new BigDecimal("10.00000");
    var redeemUnits = new BigDecimal("3.00000");
    var redeemAmount = new BigDecimal("300.00");
    var navPerUnit = new BigDecimal("100.00");
    var customerIban = "EE777888999000111222";
    var paymentId = randomUUID();
    setupUserWithFundUnits(initialAmount, initialUnits, navPerUnit, paymentId);
    var userUnitsBefore = getUserUnitsAccount().getBalance();
    var userReservedUnitsBefore = getUserReservedUnitsAccount().getBalance();
    var userRedemptionsBefore = getUserRedemptionsAccount().getBalance();
    var payoutsCashClearingBefore = getPayoutsCashClearingAccount().getBalance();

    var redemptionRequestId = randomUUID();
    var reserveTx =
        savingsFundLedger.reserveFundUnitsForRedemption(
            testParty, redeemUnits, redemptionRequestId);
    var redemptionTx =
        savingsFundLedger.redeemFundUnitsFromReserved(
            testParty, redeemUnits, redeemAmount, navPerUnit, redemptionRequestId);
    var cashTransferTx =
        savingsFundLedger.transferFromFundAccount(redeemAmount, redemptionRequestId);
    var payoutTx =
        savingsFundLedger.recordRedemptionPayout(
            testParty, redeemAmount, customerIban, redemptionRequestId);

    verifyDoubleEntry(reserveTx);
    verifyDoubleEntry(redemptionTx);
    verifyDoubleEntry(cashTransferTx);
    verifyDoubleEntry(payoutTx);

    assertThat(deltaSince(userUnitsBefore, getUserUnitsAccount()))
        .isEqualByComparingTo(redeemUnits);
    assertThat(deltaSince(userReservedUnitsBefore, getUserReservedUnitsAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userRedemptionsBefore, getUserRedemptionsAccount()))
        .isEqualByComparingTo(redeemAmount);
    assertThat(deltaSince(payoutsCashClearingBefore, getPayoutsCashClearingAccount()))
        .isEqualByComparingTo(ZERO);
  }

  @Test
  void recordPaymentReceived_autoCreatesPartyAndAccountsForNewUser() {
    var newParty = new PartyId(PartyId.Type.PERSON, "99999999999");
    var amount = new BigDecimal("100.00");
    var externalReference = randomUUID();

    var transaction = savingsFundLedger.recordPaymentReceived(newParty, amount, externalReference);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("PAYMENT_RECEIVED");
    assertThat(transaction.getMetadata().get("partyCode")).isEqualTo("99999999999");
    assertThat(transaction.getMetadata().get("partyType")).isEqualTo("PERSON");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    verifyDoubleEntry(transaction);
  }

  @Test
  void subscribeAndRedeemRoundTrip_allBalancesReturnToZero() {
    var cashAmount = new BigDecimal("1000.00");
    var fundUnits = new BigDecimal("10.00000");
    var navPerUnit = new BigDecimal("100.00");
    var customerIban = "EE123456789012345678";
    var userCashBefore = getUserCashAccount().getBalance();
    var userCashReservedBefore = getUserCashReservedAccount().getBalance();
    var userCashRedemptionBefore = getUserCashRedemptionAccount().getBalance();
    var userUnitsBefore = getUserUnitsAccount().getBalance();
    var userReservedUnitsBefore = getUserReservedUnitsAccount().getBalance();
    var fundUnitsOutstandingBefore = getFundUnitsOutstandingAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    var fundInvestmentCashClearingBefore = getFundInvestmentCashClearingAccount().getBalance();
    var payoutsCashClearingBefore = getPayoutsCashClearingAccount().getBalance();
    var userSubscriptionsBefore = getUserSubscriptionsAccount().getBalance();
    var userRedemptionsBefore = getUserRedemptionsAccount().getBalance();

    var paymentId = randomUUID();
    savingsFundLedger.recordPaymentReceived(testParty, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(testParty, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        testParty, cashAmount, fundUnits, navPerUnit, paymentId);
    savingsFundLedger.transferToFundAccount(cashAmount, paymentId);

    assertThat(deltaSince(userUnitsBefore, getUserUnitsAccount()))
        .isEqualByComparingTo(fundUnits.negate());
    assertThat(deltaSince(fundUnitsOutstandingBefore, getFundUnitsOutstandingAccount()))
        .isEqualByComparingTo(fundUnits);
    assertThat(deltaSince(userSubscriptionsBefore, getUserSubscriptionsAccount()))
        .isEqualByComparingTo(cashAmount.negate());

    var redemptionRequestId = randomUUID();
    savingsFundLedger.reserveFundUnitsForRedemption(testParty, fundUnits, redemptionRequestId);
    savingsFundLedger.redeemFundUnitsFromReserved(
        testParty, fundUnits, cashAmount, navPerUnit, redemptionRequestId);
    savingsFundLedger.transferFromFundAccount(cashAmount, redemptionRequestId);
    savingsFundLedger.recordRedemptionPayout(
        testParty, cashAmount, customerIban, redemptionRequestId);

    assertThat(deltaSince(userCashBefore, getUserCashAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashReservedBefore, getUserCashReservedAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashRedemptionBefore, getUserCashRedemptionAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userUnitsBefore, getUserUnitsAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userReservedUnitsBefore, getUserReservedUnitsAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(fundUnitsOutstandingBefore, getFundUnitsOutstandingAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(fundInvestmentCashClearingBefore, getFundInvestmentCashClearingAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(payoutsCashClearingBefore, getPayoutsCashClearingAccount()))
        .isEqualByComparingTo(ZERO);

    assertThat(deltaSince(userSubscriptionsBefore, getUserSubscriptionsAccount()))
        .isEqualByComparingTo(cashAmount.negate());
    assertThat(deltaSince(userRedemptionsBefore, getUserRedemptionsAccount()))
        .isEqualByComparingTo(cashAmount);
  }

  @Test
  void recordAdjustment_systemToSystem_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("50.00");
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    var bankAdjustmentBefore = getSystemAccount(BANK_ADJUSTMENT).getBalance();

    var transaction =
        savingsFundLedger.recordAdjustment(
            "INCOMING_PAYMENTS_CLEARING",
            (PartyId) null,
            "BANK_ADJUSTMENT",
            (PartyId) null,
            amount,
            null,
            "Test adjustment");

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("ADJUSTMENT");
    assertThat(transaction.getMetadata().get("description")).isEqualTo("Test adjustment");
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(amount);
    assertThat(deltaSince(bankAdjustmentBefore, getSystemAccount(BANK_ADJUSTMENT)))
        .isEqualByComparingTo(amount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordAdjustment_userToSystem_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("25.00");
    var paymentId = randomUUID();
    savingsFundLedger.recordPaymentReceived(testParty, amount, paymentId);

    var transaction =
        savingsFundLedger.recordAdjustment(
            "CASH",
            testParty,
            "INCOMING_PAYMENTS_CLEARING",
            null,
            amount,
            null,
            "User to system adjustment");

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("ADJUSTMENT");
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordAdjustment_differentParties_throwsException() {
    var paymentId = randomUUID();
    savingsFundLedger.recordPaymentReceived(testParty, new BigDecimal("100.00"), paymentId);

    var otherParty = new PartyId(PartyId.Type.PERSON, "38001010002");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            savingsFundLedger.recordAdjustment(
                "CASH",
                testParty,
                "CASH",
                otherParty,
                new BigDecimal("10.00"),
                null,
                "Invalid cross-party"));
  }

  @Test
  void recordAdjustment_samePartyDifferentAccounts_succeeds() {
    var paymentId = randomUUID();
    savingsFundLedger.recordPaymentReceived(testParty, new BigDecimal("100.00"), paymentId);
    savingsFundLedger.reservePaymentForSubscription(testParty, new BigDecimal("100.00"), paymentId);

    var transaction =
        savingsFundLedger.recordAdjustment(
            "CASH_RESERVED",
            testParty,
            "CASH",
            testParty,
            new BigDecimal("10.00"),
            null,
            "Reverse duplicate reservation");

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("ADJUSTMENT");
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordAdjustment_withDynamicSystemAccounts_createsAccountsOnTheFly() {
    var units = new BigDecimal("11704.00000");

    var transaction =
        savingsFundLedger.recordAdjustment(
            "TRADE_UNIT_SETTLEMENT:TKF100:LU1291102447",
            (PartyId) null,
            "SECURITIES_CUSTODY:TKF100:LU1291102447",
            (PartyId) null,
            units,
            null,
            "Trade unit backfill");

    assertThat(transaction.getTransactionType())
        .isEqualTo(LedgerTransaction.TransactionType.ADJUSTMENT);
    assertThat(transaction.getEntries()).hasSize(2);
    assertThat(getSecurityUnitsAccount("LU1291102447").getBalance()).isEqualByComparingTo(units);
    assertThat(getSecuritiesCustodyAccount("LU1291102447").getBalance())
        .isEqualByComparingTo(units.negate());
    verifyDoubleEntry(transaction);
  }

  private void setupUserWithFundUnits(
      BigDecimal cashAmount, BigDecimal fundUnits, BigDecimal navPerUnit, UUID paymentId) {
    savingsFundLedger.recordPaymentReceived(testParty, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(testParty, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        testParty, cashAmount, fundUnits, navPerUnit, paymentId);
    savingsFundLedger.transferToFundAccount(cashAmount, paymentId);
  }

  private LedgerAccount getUserAccount(UserAccount userAccount) {
    return ledgerService.getPartyAccount(testParty.code(), PERSON, userAccount);
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerService.getSystemAccount(systemAccount, TKF100);
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

  private LedgerAccount getSecurityUnitsAccount(String isin) {
    return ledgerAccountService
        .findSystemAccountByName("TRADE_UNIT_SETTLEMENT:TKF100:" + isin, ASSET, FUND_UNIT)
        .orElseThrow();
  }

  private LedgerAccount getSecuritiesCustodyAccount(String isin) {
    return ledgerAccountService
        .findSystemAccountByName("SECURITIES_CUSTODY:TKF100:" + isin, LIABILITY, FUND_UNIT)
        .orElseThrow();
  }

  @Test
  void bounceBackUnattributedPayment_isIdempotent() {
    var amount = new BigDecimal("300.00");
    var externalReference = randomUUID();
    var unreconciledBefore = getUnreconciledBankReceiptsAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    savingsFundLedger.recordUnattributedPayment(amount, externalReference);

    var first = savingsFundLedger.bounceBackUnattributedPayment(amount, externalReference);
    var second = savingsFundLedger.bounceBackUnattributedPayment(amount, externalReference);

    assertThat(second).isEqualTo(first);
    assertThat(deltaSince(unreconciledBefore, getUnreconciledBankReceiptsAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
  }

  @Test
  void recordPaymentCancelled_isIdempotent() {
    var amount = new BigDecimal("500.00");
    var externalReference = randomUUID();
    var userCashBefore = getUserCashAccount().getBalance();
    var userCashReservedBefore = getUserCashReservedAccount().getBalance();
    var clearingBefore = getIncomingPaymentsClearingAccount().getBalance();
    savingsFundLedger.recordPaymentReceived(testParty, amount, externalReference);
    savingsFundLedger.reservePaymentForCancellation(testParty, amount, externalReference);

    var first = savingsFundLedger.recordPaymentCancelled(testParty, amount, externalReference);
    var second = savingsFundLedger.recordPaymentCancelled(testParty, amount, externalReference);

    assertThat(second).isEqualTo(first);
    assertThat(deltaSince(userCashBefore, getUserCashAccount())).isEqualByComparingTo(ZERO);
    assertThat(deltaSince(userCashReservedBefore, getUserCashReservedAccount()))
        .isEqualByComparingTo(ZERO);
    assertThat(deltaSince(clearingBefore, getIncomingPaymentsClearingAccount()))
        .isEqualByComparingTo(ZERO);
  }

  private BigDecimal deltaSince(BigDecimal before, LedgerAccount account) {
    return account.getBalance().subtract(before);
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
