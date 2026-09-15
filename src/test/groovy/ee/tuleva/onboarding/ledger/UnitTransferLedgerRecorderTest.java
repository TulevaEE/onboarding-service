package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
  SavingsFundLedgerAccounts.class,
  RedemptionLedgerRecorder.class,
  UnattributedPaymentLedgerRecorder.class,
  UnitTransferLedgerRecorder.class,
  SavingsFundLedger.class,
  ClockConfig.class
})
class UnitTransferLedgerRecorderTest {

  @Autowired LedgerService ledgerService;
  @Autowired SavingsFundLedger savingsFundLedger;

  PartyRef giver = new PartyRef(PERSON, "38888888888");
  PartyRef receiver = new PartyRef(PERSON, "39999999999");

  @BeforeEach
  void onboardTheReceiver() {
    ledgerService.initializeAccounts(receiver);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void transferMovesUnitsToTheReceiver() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    savingsFundLedger.recordUnitTransfer(giver, receiver, new BigDecimal("40.00000"), randomUUID());

    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo("60.00000");
    assertThat(holding(receiver, FUND_UNITS)).isEqualByComparingTo("40.00000");
  }

  @Test
  void transferCarriesTheProportionalPaidInAmount() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    savingsFundLedger.recordUnitTransfer(giver, receiver, new BigDecimal("40.00000"), randomUUID());

    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("600.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("400.00");
  }

  @Test
  void transferOfEverythingLeavesNoPaidInAmountBehind() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("3.00000"));

    savingsFundLedger.recordUnitTransfer(giver, receiver, new BigDecimal("3.00000"), randomUUID());

    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo(ZERO);
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo(ZERO);
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("1000.00");
  }

  @Test
  void transferLeavesTheFundsOutstandingUnitsUntouched() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    var outstandingBefore =
        ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100).getBalance();

    savingsFundLedger.recordUnitTransfer(giver, receiver, new BigDecimal("40.00000"), randomUUID());

    assertThat(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100).getBalance())
        .isEqualByComparingTo(outstandingBefore);
  }

  @Test
  void transferBalancesWithinEachAssetType() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    var transaction =
        savingsFundLedger.recordUnitTransfer(
            giver, receiver, new BigDecimal("40.00000"), randomUUID());

    assertThat(transaction.getEntries()).hasSize(4);
    assertThat(sumOf(transaction, LedgerAccount.AssetType.FUND_UNIT)).isEqualByComparingTo(ZERO);
    assertThat(sumOf(transaction, LedgerAccount.AssetType.EUR)).isEqualByComparingTo(ZERO);
  }

  @Test
  void transferRecordsBothPartiesInTheMetadata() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    var transaction =
        savingsFundLedger.recordUnitTransfer(
            giver, receiver, new BigDecimal("40.00000"), randomUUID());

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("UNIT_TRANSFER");
    assertThat(transaction.getMetadata().get("partyCode")).isEqualTo(giver.code());
    assertThat(transaction.getMetadata().get("recipientCode")).isEqualTo(receiver.code());
  }

  @Test
  void transferOfMoreUnitsThanHeldIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(
            () ->
                savingsFundLedger.recordUnitTransfer(
                    giver, receiver, new BigDecimal("100.00001"), randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("more units than the party holds");
  }

  @Test
  void transferOfUnitsReservedForRedemptionIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    savingsFundLedger.reserveFundUnitsForRedemption(
        giver, new BigDecimal("100.00000"), randomUUID());

    assertThatThrownBy(
            () ->
                savingsFundLedger.recordUnitTransfer(
                    giver, receiver, new BigDecimal("1.00000"), randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("more units than the party holds");
  }

  @Test
  void transferOfEveryFreeUnitLeavesTheReservedUnitsTheirShareOfThePaidInAmount() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    savingsFundLedger.reserveFundUnitsForRedemption(
        giver, new BigDecimal("50.00000"), randomUUID());

    savingsFundLedger.recordUnitTransfer(giver, receiver, new BigDecimal("50.00000"), randomUUID());

    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
  }

  @Test
  void repeatingATransferWithTheSameReferenceRecordsItOnce() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    var externalReference = randomUUID();

    var first =
        savingsFundLedger.recordUnitTransfer(
            giver, receiver, new BigDecimal("40.00000"), externalReference);
    var second =
        savingsFundLedger.recordUnitTransfer(
            giver, receiver, new BigDecimal("40.00000"), externalReference);

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(holding(receiver, FUND_UNITS)).isEqualByComparingTo("40.00000");
    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo("60.00000");
  }

  @Test
  void transferOfUnitsFinerThanTheFundPricesThemIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(
            () ->
                savingsFundLedger.recordUnitTransfer(
                    giver, receiver, new BigDecimal("0.000004"), randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("finer than the fund prices them");
  }

  @Test
  void transferToAPartyWithNoLedgerPresenceIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    var strangerWithATypo = new PartyRef(PERSON, "37777777777");

    assertThatThrownBy(
            () ->
                savingsFundLedger.recordUnitTransfer(
                    giver, strangerWithATypo, new BigDecimal("1.00000"), randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ledger party not found");
  }

  @Test
  void transferToOneselfIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(
            () ->
                savingsFundLedger.recordUnitTransfer(
                    giver, giver, new BigDecimal("1.00000"), randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("same party");
  }

  @Test
  void transferOfANonPositiveAmountIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(
            () -> savingsFundLedger.recordUnitTransfer(giver, receiver, ZERO, randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }

  private void givenUnitsWorth(PartyRef party, BigDecimal cashAmount, BigDecimal fundUnits) {
    var paymentId = randomUUID();
    var navPerUnit = cashAmount.divide(fundUnits, 5, HALF_UP);
    savingsFundLedger.recordPaymentReceived(party, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(party, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        party, cashAmount, fundUnits, navPerUnit, paymentId);
  }

  private BigDecimal holding(PartyRef party, UserAccount userAccount) {
    return ledgerService
        .getPartyAccount(party.code(), party.type(), userAccount)
        .getBalance()
        .negate();
  }

  private BigDecimal sumOf(LedgerTransaction transaction, LedgerAccount.AssetType assetType) {
    return transaction.getEntries().stream()
        .filter(entry -> entry.getAssetType() == assetType)
        .map(LedgerEntry::getAmount)
        .reduce(ZERO, BigDecimal::add);
  }
}
