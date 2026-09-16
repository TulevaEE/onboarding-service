package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_COUNT_UPDATE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH_REDEMPTION;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH_RESERVED;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS_RESERVED;
import static ee.tuleva.onboarding.ledger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.MutableClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(SavingsFundLedgerStackConfiguration.class)
class UnitTransferLedgerRecorderTest {

  @Autowired LedgerService ledgerService;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired LedgerTransactionService ledgerTransactionService;
  @Autowired TestEntityManager entityManager;

  PartyRef giver = new PartyRef(PERSON, "38888888888");
  PartyRef receiver = new PartyRef(PERSON, "39999999999");
  PartyRef thirdParty = new PartyRef(PERSON, "36666666666");

  BigDecimal recipientAcquisitionCost = new BigDecimal("250.00");
  LocalDate PRICED_ON = LocalDate.parse("2025-03-10");

  MutableClock clock = new MutableClock();

  @BeforeEach
  void onboardTheReceiver() {
    ClockHolder.setClock(clock);
    ledgerService.initializeAccounts(receiver);
  }

  @AfterEach
  void letTimeRunOnItsOwnAgain() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void transferMovesUnitsToTheReceiver() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    transfer(new BigDecimal("40.00000"));

    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo("60.00000");
    assertThat(holding(receiver, FUND_UNITS)).isEqualByComparingTo("40.00000");
  }

  @Test
  void transferMovesWhatTheGiverPaidForTheUnitsTheyGiveAway() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    transfer(new BigDecimal("40.00000"));

    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("600.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("400.00");
  }

  @Test
  void givingAwayEveryUnitMovesEveryEuroPaidInWithoutLeavingACentBehind() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("3.00000"));

    transfer(new BigDecimal("3.00000"));

    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo(ZERO);
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo(ZERO);
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("1000.00");
  }

  @Test
  void aShareOfThePaidInAmountThatDoesNotDivideEvenlyIsRoundedToWholeCents() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("3.00000"));

    transfer(new BigDecimal("1.00000"));

    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("333.33");
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("666.67");
  }

  @Test
  void givingTheLastUnitsAwayLaterMovesTheRestOfWhatTheGiverPaidInWithThem() {
    givenUnitsWorth(giver, new BigDecimal("10.01"), new BigDecimal("2.00000"));

    transfer(new BigDecimal("1.00000"));
    transfer(new BigDecimal("1.00000"));

    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo(ZERO);
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo(ZERO);
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("10.01");
  }

  @Test
  void aShareOfExactlyHalfACentIsRoundedUpToTheRecipient() {
    givenUnitsWorth(giver, new BigDecimal("10.01"), new BigDecimal("2.00000"));

    transfer(new BigDecimal("1.00000"));

    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("5.01");
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("5.00");
  }

  @Test
  void whatMovesIsWhatTheGiverPaidOnAverageForTheUnitsTheyGiveAway() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("50.00000"), new BigDecimal("500.00"));

    transfer(new BigDecimal("50.00000"));

    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
  }

  @Test
  void theGainOnUnitsTheGiverAlreadySoldDoesNotShrinkWhatTheRemainingUnitsCost() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("50.00000"), new BigDecimal("1000.00"));

    transfer(new BigDecimal("50.00000"));

    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
  }

  @Test
  void aGiverWhoTookOutMoreThanTheyPutInStillMovesWhatTheRemainingUnitsCost() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("60.00000"), new BigDecimal("1200.00"));

    transfer(new BigDecimal("40.00000"));

    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("600.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("400.00");
  }

  @Test
  void unitsThatArrivedAsATransferCountAsUnitsTheirNewOwnerPaidFor() {
    ledgerService.initializeAccounts(thirdParty);
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    transfer(new BigDecimal("40.00000"));
    givenRedeemed(receiver, new BigDecimal("20.00000"), new BigDecimal("400.00"));

    transfer(receiver, thirdParty, new BigDecimal("10.00000"), randomUUID());

    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("300.00");
    assertThat(holding(thirdParty, SUBSCRIPTIONS)).isEqualByComparingTo("100.00");
  }

  @Test
  void unitsHandedBackFromACancelledReservationAreNotCountedASecondTime() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    var redemptionRequestId = randomUUID();
    clock.tick();
    savingsFundLedger.reserveFundUnitsForRedemption(
        giver, new BigDecimal("50.00000"), redemptionRequestId);
    clock.tick();
    savingsFundLedger.cancelRedemptionReservation(
        giver, new BigDecimal("50.00000"), redemptionRequestId);

    transfer(new BigDecimal("50.00000"));

    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
  }

  @Test
  void whatTheGiverOnceOwnedAndSoldIsNotAveragedIntoWhatTheirCurrentUnitsCost() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("100.00000"), new BigDecimal("1000.00"));
    givenUnitsWorth(giver, new BigDecimal("2000.00"), new BigDecimal("100.00000"));

    transfer(new BigDecimal("100.00000"));

    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("1000.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("2000.00");
  }

  @Test
  void unitsBoughtAfterAPartialRedemptionAreAveragedWithTheOnesStillHeld() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("50.00000"), new BigDecimal("500.00"));
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("50.00000"));

    transfer(new BigDecimal("50.00000"));

    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("750.00");
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("1250.00");
  }

  @Test
  void givingEveryRemainingUnitAwayAfterARedemptionMovesTheirWholeCostExactly() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("50.00000"), new BigDecimal("500.00"));
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("50.00000"));

    transfer(new BigDecimal("100.00000"));

    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo(ZERO);
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("1500.00");
  }

  @Test
  void whatArrivedWithTransferredUnitsStaysInTheCostOfWhatTheirNewOwnerStillHolds() {
    ledgerService.initializeAccounts(thirdParty);
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    transfer(new BigDecimal("40.00000"));
    givenRedeemed(receiver, new BigDecimal("20.00000"), new BigDecimal("400.00"));
    givenUnitsWorth(receiver, new BigDecimal("800.00"), new BigDecimal("20.00000"));

    transfer(receiver, thirdParty, new BigDecimal("40.00000"), randomUUID());

    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("200.00");
    assertThat(holding(thirdParty, SUBSCRIPTIONS)).isEqualByComparingTo("1000.00");
  }

  @Test
  void theUnitsWhoseCostIsQuotedAreTheOnesTheGiverStillHoldsFreeOrReserved() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("20.00000"), new BigDecimal("400.00"));
    givenUnitsWorth(giver, new BigDecimal("600.00"), new BigDecimal("20.00000"));
    clock.tick();
    savingsFundLedger.reserveFundUnitsForRedemption(
        giver, new BigDecimal("30.00000"), randomUUID());

    var quote = savingsFundLedger.quoteUnitTransfer(giver, receiver, new BigDecimal("10.00000"));

    assertThat(quote.giverUnitsOwned())
        .isEqualByComparingTo(holding(giver, FUND_UNITS).add(holding(giver, FUND_UNITS_RESERVED)));
  }

  @Test
  void unitsThatMovedUnderATypeTheirCostCannotBeReplayedFromStopTheTransfer() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    clock.tick();
    ledgerTransactionService.createTransaction(
        UNIT_COUNT_UPDATE,
        Instant.now(clock),
        randomUUID(),
        Map.of(),
        new LedgerEntryDto(
            ledgerService.getPartyAccount(giver.code(), giver.type(), FUND_UNITS),
            new BigDecimal("-10.00000")),
        new LedgerEntryDto(
            ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100),
            new BigDecimal("10.00000")));

    assertThatThrownBy(() -> transfer(new BigDecimal("1.00000")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aSubscriptionRecordedUnderTheOldGenericTypeCountsTowardsWhatTheUnitsCost() {
    givenLegacyUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenUnitsWorth(giver, new BigDecimal("3000.00"), new BigDecimal("100.00000"));

    transfer(new BigDecimal("100.00000"));

    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("2000.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("2000.00");
  }

  @Test
  void aRedemptionRecordedUnderTheOldGenericTypeTakesItsShareOfWhatTheUnitsCost() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenLegacyRedeemed(giver, new BigDecimal("50.00000"), new BigDecimal("800.00"));

    transfer(new BigDecimal("50.00000"));

    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
  }

  @Test
  void unitsThatMovedUnderTheOldGenericTypeWithoutNamingTheOperationStopTheTransfer() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    clock.tick();
    recordLegacyTransaction(
        Map.of(),
        randomUUID(),
        partyEntry(giver, FUND_UNITS, new BigDecimal("-10.00000")),
        systemEntry(new BigDecimal("10.00000")));

    assertThatThrownBy(() -> transfer(new BigDecimal("1.00000")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aTransferIsRefusedWhenMoreWasTakenBackThanTheGiversUnitsStillCost() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("50.00000"), new BigDecimal("500.00"));
    givenTakenBackFromWhatTheyPaidIn(giver, new BigDecimal("600.00"));

    assertThatThrownBy(() -> transfer(new BigDecimal("10.00000")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aTransferIsRefusedWhenTheGiversUnitsCostMoreThanIsLeftOfWhatTheyPaidIn() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenTakenBackFromWhatTheyPaidIn(giver, new BigDecimal("1500.00"));
    givenRedeemed(giver, new BigDecimal("100.00000"), new BigDecimal("1000.00"));
    givenUnitsWorth(giver, new BigDecimal("100.00"), new BigDecimal("10.00000"));

    assertThatThrownBy(() -> transfer(new BigDecimal("10.00000")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void transferLeavesTheFundsOutstandingUnitsUntouched() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    var outstandingBefore =
        ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100).getBalance();

    transfer(new BigDecimal("40.00000"));

    assertThat(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100).getBalance())
        .isEqualByComparingTo(outstandingBefore);
  }

  @Test
  void transferMovesUnitsAndWhatWasPaidForThemAndNothingElse() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    var transaction = transfer(new BigDecimal("40.00000"));

    assertThat(transaction.getEntries())
        .extracting(LedgerEntry::getAssetType)
        .containsExactlyInAnyOrder(FUND_UNIT, FUND_UNIT, EUR, EUR);
    assertThat(transaction.sum()).isEqualByComparingTo(ZERO);
  }

  @Test
  void transferRecordsBothPartiesInTheMetadata() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    var transaction = transfer(new BigDecimal("40.00000"));

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("UNIT_TRANSFER");
    assertThat(transaction.getMetadata().get("partyCode")).isEqualTo(giver.code());
    assertThat(transaction.getMetadata().get("recipientCode")).isEqualTo(receiver.code());
  }

  @Test
  void transferRecordsWhatTheUnitsCostTheRecipientAndNoPriceForTheUnitsThemselves() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    var transaction = transfer(new BigDecimal("40.00000"));

    assertThat(transaction.findNavPerUnit()).isEmpty();
    assertThat(transaction.getMetadata().get("recipientAcquisitionCostEur"))
        .isEqualTo(recipientAcquisitionCost);
  }

  @Test
  void quotingAnswersTheFiguresTheTransferWouldLeaveBehindAndWritesNothing() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    savingsFundLedger.reserveFundUnitsForRedemption(
        giver, new BigDecimal("30.00000"), randomUUID());
    var transactionsBefore = transactionCount();

    var quote = savingsFundLedger.quoteUnitTransfer(giver, receiver, new BigDecimal("40"));

    assertThat(quote)
        .isEqualTo(
            new UnitTransferQuote(
                new BigDecimal("40.00000"),
                new BigDecimal("30.00000"),
                new BigDecimal("40.00000"),
                new BigDecimal("1000.00"),
                new BigDecimal("100.00000"),
                new BigDecimal("1000.00"),
                new BigDecimal("400.00")));
    assertThat(transactionCount()).isEqualTo(transactionsBefore);
    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo("70.00000");
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("1000.00");
    assertThat(holding(receiver, FUND_UNITS)).isEqualByComparingTo(ZERO);
  }

  @Test
  void transferOfMoreUnitsThanHeldIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(() -> transfer(new BigDecimal("100.00001")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void transferOfUnitsReservedForRedemptionIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    savingsFundLedger.reserveFundUnitsForRedemption(
        giver, new BigDecimal("100.00000"), randomUUID());

    assertThatThrownBy(() -> transfer(new BigDecimal("1.00000")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlyTheFreeUnitsCanBeGivenAwayWhenSomeAreReservedForRedemption() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    savingsFundLedger.reserveFundUnitsForRedemption(
        giver, new BigDecimal("50.00000"), randomUUID());

    transfer(new BigDecimal("50.00000"));

    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo(ZERO);
    assertThat(holding(receiver, FUND_UNITS)).isEqualByComparingTo("50.00000");
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("500.00");
  }

  @Test
  void repeatingATransferWithTheSameReferenceRecordsItOnce() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    var externalReference = randomUUID();

    var first = transfer(new BigDecimal("40.00000"), externalReference);
    var second = transfer(new BigDecimal("40.00000"), externalReference);

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(holding(receiver, FUND_UNITS)).isEqualByComparingTo("40.00000");
    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo("60.00000");
    assertThat(holding(receiver, SUBSCRIPTIONS)).isEqualByComparingTo("400.00");
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("600.00");
  }

  @Test
  void transferOfUnitsFinerThanTheFundPricesThemIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(() -> transfer(new BigDecimal("0.000004")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void transferToAPartyWithNoLedgerPresenceIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    var strangerWithATypo = new PartyRef(PERSON, "37777777777");

    assertThatThrownBy(
            () ->
                savingsFundLedger.recordUnitTransfer(
                    instruction(strangerWithATypo, new BigDecimal("1.00000"), randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void transferToOneselfIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(
            () ->
                savingsFundLedger.recordUnitTransfer(
                    instruction(giver, new BigDecimal("1.00000"), randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void transferOfANonPositiveAmountIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(() -> transfer(ZERO)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anAcquisitionCostBelowZeroIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(() -> transferCosting(new BigDecimal("-0.01")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anAcquisitionCostFinerThanTheCentsItIsHeldInIsRefused() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    assertThatThrownBy(() -> transferCosting(new BigDecimal("12.345")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anAcquisitionCostInWholeCentsIsRecorded() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    var transaction = transferCosting(new BigDecimal("12.5"));

    assertThat(transaction.findRecipientAcquisitionCost()).contains(new BigDecimal("12.5"));
  }

  private LedgerTransaction transfer(BigDecimal units) {
    return transfer(units, randomUUID());
  }

  private LedgerTransaction transfer(BigDecimal units, UUID externalReference) {
    return transfer(giver, receiver, units, externalReference);
  }

  private LedgerTransaction transfer(
      PartyRef from, PartyRef to, BigDecimal units, UUID externalReference) {
    clock.tick();
    return savingsFundLedger.recordUnitTransfer(instruction(from, to, units, externalReference));
  }

  private LedgerTransaction transferCosting(BigDecimal acquisitionCost) {
    clock.tick();
    return savingsFundLedger.recordUnitTransfer(
        new UnitTransferInstruction(
            giver, receiver, new BigDecimal("40.00000"), acquisitionCost, randomUUID()));
  }

  private UnitTransferInstruction instruction(
      PartyRef to, BigDecimal units, UUID externalReference) {
    return instruction(giver, to, units, externalReference);
  }

  private UnitTransferInstruction instruction(
      PartyRef from, PartyRef to, BigDecimal units, UUID externalReference) {
    return new UnitTransferInstruction(
        from, to, units, recipientAcquisitionCost, externalReference);
  }

  private void givenRedeemed(PartyRef party, BigDecimal fundUnits, BigDecimal cashAmount) {
    var redemptionRequestId = randomUUID();
    var soldAt = cashAmount.divide(fundUnits, 5, HALF_UP);
    clock.tick();
    savingsFundLedger.reserveFundUnitsForRedemption(party, fundUnits, redemptionRequestId);
    clock.tick();
    savingsFundLedger.redeemFundUnitsFromReserved(
        party, fundUnits, cashAmount, soldAt, PRICED_ON, redemptionRequestId);
  }

  private void givenUnitsWorth(PartyRef party, BigDecimal cashAmount, BigDecimal fundUnits) {
    var paymentId = randomUUID();
    var boughtAt = cashAmount.divide(fundUnits, 5, HALF_UP);
    clock.tick();
    savingsFundLedger.recordPaymentReceived(party, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(party, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        party, cashAmount, fundUnits, boughtAt, PRICED_ON, paymentId);
  }

  private void givenTakenBackFromWhatTheyPaidIn(PartyRef party, BigDecimal amount) {
    clock.tick();
    savingsFundLedger.recordAdjustment(
        SUBSCRIPTIONS.name(),
        party,
        INCOMING_PAYMENTS_CLEARING.name(),
        null,
        amount,
        randomUUID(),
        "Payment returned to the payer");
  }

  private void givenLegacyUnitsWorth(PartyRef party, BigDecimal cashAmount, BigDecimal fundUnits) {
    var paymentId = randomUUID();
    clock.tick();
    savingsFundLedger.recordPaymentReceived(party, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(party, cashAmount, paymentId);
    clock.tick();
    recordLegacyTransaction(
        operationType(FUND_SUBSCRIPTION),
        paymentId,
        partyEntry(party, CASH_RESERVED, cashAmount),
        partyEntry(party, SUBSCRIPTIONS, cashAmount.negate()),
        partyEntry(party, FUND_UNITS, fundUnits.negate()),
        systemEntry(fundUnits));
  }

  private void givenLegacyRedeemed(PartyRef party, BigDecimal fundUnits, BigDecimal cashAmount) {
    var redemptionRequestId = randomUUID();
    clock.tick();
    savingsFundLedger.reserveFundUnitsForRedemption(party, fundUnits, redemptionRequestId);
    clock.tick();
    recordLegacyTransaction(
        operationType(REDEMPTION_REQUEST),
        redemptionRequestId,
        partyEntry(party, FUND_UNITS_RESERVED, fundUnits),
        systemEntry(fundUnits.negate()),
        partyEntry(party, CASH_REDEMPTION, cashAmount.negate()),
        partyEntry(party, REDEMPTIONS, cashAmount));
  }

  private LedgerTransaction recordLegacyTransaction(
      Map<String, Object> metadata, UUID externalReference, LedgerEntryDto... entries) {
    return ledgerTransactionService.createTransaction(
        TRANSFER, Instant.now(clock), externalReference, metadata, entries);
  }

  private Map<String, Object> operationType(TransactionType transactionType) {
    return Map.of(OPERATION_TYPE.getKey(), transactionType.name());
  }

  private LedgerEntryDto partyEntry(PartyRef party, UserAccount userAccount, BigDecimal amount) {
    return new LedgerEntryDto(
        ledgerService.getPartyAccount(party.code(), party.type(), userAccount), amount);
  }

  private LedgerEntryDto systemEntry(BigDecimal amount) {
    return new LedgerEntryDto(
        ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100), amount);
  }

  private long transactionCount() {
    return entityManager
        .getEntityManager()
        .createQuery("SELECT COUNT(transaction) FROM LedgerTransaction transaction", Long.class)
        .getSingleResult();
  }

  private BigDecimal holding(PartyRef party, UserAccount userAccount) {
    return ledgerService
        .getPartyAccount(party.code(), party.type(), userAccount)
        .getBalance()
        .negate();
  }
}
