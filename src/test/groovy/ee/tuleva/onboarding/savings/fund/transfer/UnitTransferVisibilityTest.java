package ee.tuleva.onboarding.savings.fund.transfer;

import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.CashFlow.Type.TRANSFER_IN;
import static ee.tuleva.onboarding.epis.CashFlow.Type.TRANSFER_OUT;
import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.account.SavingsFundNav;
import ee.tuleva.onboarding.account.SavingsFundStatementService;
import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedgerStackConfiguration;
import ee.tuleva.onboarding.ledger.UnitTransferInstruction;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingsFundConfiguration;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalendar;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.MutableClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  SavingsFundLedgerStackConfiguration.class,
  SavingsFundStatementService.class,
  SavingsFundTransactionService.class,
  NavCalendar.class,
  PublicHolidays.class
})
class UnitTransferVisibilityTest {

  @Autowired LedgerService ledgerService;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired SavingsFundStatementService statements;
  @Autowired SavingsFundTransactionService transactions;
  @Autowired TestEntityManager entityManager;

  @MockitoBean SavingsFundOnboardingService onboarding;
  @MockitoBean SavingsFundConfiguration savingsFundConfiguration;
  @MockitoBean SavingsFundNav navProvider;
  @MockitoBean FundRepository fundRepository;
  @MockitoBean RedemptionRequestRepository redemptionRequests;
  @MockitoBean SavingFundPaymentRepository payments;

  Fund savingsFund = additionalSavingsFund();

  PartyRef giver = new PartyRef(PERSON, "38812121215");
  PartyRef recipient = new PartyRef(PERSON, "39901019992");

  AuthenticatedPerson giverPerson = person("38812121215");
  AuthenticatedPerson recipientPerson = person("39901019992");

  BigDecimal navPerUnit = new BigDecimal("12.0000");
  LocalDate PRICED_ON = LocalDate.parse("2025-03-10");

  MutableClock clock = new MutableClock();

  @AfterEach
  void letTimeRunOnItsOwnAgain() {
    ClockHolder.setDefaultClock();
  }

  @BeforeEach
  void bothPartiesAreOnboardedSaversOfTheSavingsFund() {
    ClockHolder.setClock(clock);
    ledgerService.initializeAccounts(giver);
    ledgerService.initializeAccounts(recipient);
    given(onboarding.isOnboardingCompleted(any(PartyId.class))).willReturn(true);
    given(savingsFundConfiguration.getIsin()).willReturn(savingsFund.getIsin());
    given(fundRepository.findByIsin(savingsFund.getIsin())).willReturn(savingsFund);
    given(navProvider.getDisplayNav(TKF100)).willReturn(navPerUnit);
  }

  @Test
  void theUnitsAndWhatWasPaidForThemBothMoveToTheRecipient() {
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    transferToTheRecipient(new BigDecimal("40.00000"));

    assertThat(statementOf(giverPerson).getUnits()).isEqualByComparingTo("60.00000");
    assertThat(statementOf(giverPerson).getContributions()).isEqualByComparingTo("600.00");
    assertThat(statementOf(recipientPerson).getUnits()).isEqualByComparingTo("40.00000");
    assertThat(statementOf(recipientPerson).getContributions()).isEqualByComparingTo("400.00");
  }

  @Test
  void theProfitFollowsTheUnitsInsteadOfStayingBehindWithTheGiver() {
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    assertThat(statementOf(giverPerson).getProfit()).isEqualByComparingTo("200.00");

    transferToTheRecipient(new BigDecimal("40.00000"));

    assertThat(statementOf(giverPerson).getProfit()).isEqualByComparingTo("120.00");
    assertThat(statementOf(recipientPerson).getProfit()).isEqualByComparingTo("80.00");
  }

  @Test
  void aRecipientOfEveryUnitIsShownEveryEuroTheGiverPaidIn() {
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("3.00000"));

    transferToTheRecipient(new BigDecimal("3.00000"));

    assertThat(statementOf(giverPerson).getUnits()).isEqualByComparingTo("0.00000");
    assertThat(statementOf(giverPerson).getContributions()).isEqualByComparingTo("0.00");
    assertThat(statementOf(recipientPerson).getContributions()).isEqualByComparingTo("1000.00");
  }

  @Test
  void neitherPartyIsShownAProfitTheyDidNotMakeWhenTheGiverHadAlreadyRedeemed() {
    navIs(new BigDecimal("10.0000"));
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenTheGiverRedeemed(new BigDecimal("50.00000"), new BigDecimal("500.00"));

    transferToTheRecipient(new BigDecimal("50.00000"));

    assertThat(statementOf(giverPerson).getContributions()).isEqualByComparingTo("500.00");
    assertThat(statementOf(giverPerson).getSubtractions()).isEqualByComparingTo("-500.00");
    assertThat(statementOf(giverPerson).getProfit()).isEqualByComparingTo("0.00");
    assertThat(statementOf(recipientPerson).getContributions()).isEqualByComparingTo("500.00");
    assertThat(statementOf(recipientPerson).getSubtractions()).isEqualByComparingTo("0.00");
    assertThat(statementOf(recipientPerson).getProfit()).isEqualByComparingTo("0.00");
  }

  @Test
  void theGainTheGiverAlreadyRealisedStaysWithThemAndTheRestFollowsTheUnits() {
    navIs(new BigDecimal("10.0000"));
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    navIs(new BigDecimal("20.0000"));
    givenTheGiverRedeemed(new BigDecimal("50.00000"), new BigDecimal("1000.00"));

    transferToTheRecipient(new BigDecimal("50.00000"));

    assertThat(statementOf(giverPerson).getContributions()).isEqualByComparingTo("500.00");
    assertThat(statementOf(giverPerson).getSubtractions()).isEqualByComparingTo("-1000.00");
    assertThat(statementOf(giverPerson).getUnits()).isEqualByComparingTo("0.00000");
    assertThat(statementOf(giverPerson).getProfit()).isEqualByComparingTo("500.00");
    assertThat(statementOf(recipientPerson).getContributions()).isEqualByComparingTo("500.00");
    assertThat(statementOf(recipientPerson).getValue()).isEqualByComparingTo("1000.00");
    assertThat(statementOf(recipientPerson).getProfit()).isEqualByComparingTo("500.00");
  }

  @Test
  void neitherPartyIsShownAProfitWhenTheGiverSoldUpAndBoughtBackBeforeGivingItAway() {
    navIs(new BigDecimal("10.0000"));
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenTheGiverRedeemed(new BigDecimal("100.00000"), new BigDecimal("1000.00"));
    givenTheGiverBought(new BigDecimal("2000.00"), new BigDecimal("100.00000"));
    navIs(new BigDecimal("20.0000"));

    transferToTheRecipient(new BigDecimal("100.00000"));

    assertThat(statementOf(giverPerson).getContributions()).isEqualByComparingTo("1000.00");
    assertThat(statementOf(giverPerson).getSubtractions()).isEqualByComparingTo("-1000.00");
    assertThat(statementOf(giverPerson).getProfit()).isEqualByComparingTo("0.00");
    assertThat(statementOf(recipientPerson).getContributions()).isEqualByComparingTo("2000.00");
    assertThat(statementOf(recipientPerson).getValue()).isEqualByComparingTo("2000.00");
    assertThat(statementOf(recipientPerson).getProfit()).isEqualByComparingTo("0.00");
  }

  @Test
  void bothPartiesSeeTheTransferInTheirTransactionHistory() {
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    transferToTheRecipient(new BigDecimal("40.00000"));

    assertThat(transactions.getTransactions(recipientPerson))
        .extracting(Transaction::amount, Transaction::units, Transaction::nav, Transaction::isin)
        .containsExactly(
            tuple(
                new BigDecimal("400.00"), new BigDecimal("40.00000"), null, savingsFund.getIsin()));
    assertThat(transactions.getTransactions(giverPerson))
        .extracting(Transaction::amount)
        .containsExactlyInAnyOrder(new BigDecimal("1000.00"), new BigDecimal("-400.00"));
  }

  @Test
  void neitherPartySeesTheTransferAsCashTheyPaidInThemselves() {
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    transferToTheRecipient(new BigDecimal("40.00000"));

    assertThat(transactions.getTransactions(recipientPerson))
        .extracting(Transaction::type)
        .containsExactly(TRANSFER_IN);
    assertThat(transactions.getTransactions(giverPerson))
        .extracting(Transaction::type)
        .containsExactlyInAnyOrder(CONTRIBUTION_CASH, TRANSFER_OUT);
    assertThat(theTransferRowOf(giverPerson).type()).isEqualTo(TRANSFER_OUT);
  }

  @Test
  void anHeirsUnitsArriveWithNothingCountedAsPaidForThem() {
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    transferToTheRecipient(new BigDecimal("40.00000"));

    assertThat(theOnlyRowOf(recipientPerson).acquisitionCost()).isEqualByComparingTo("0.00");
    assertThat(theTransferRowOf(giverPerson).acquisitionCost()).isNull();
  }

  @Test
  void aRecipientWhoPaidForTheUnitsArrivesWithThatPriceAsTheirAcquisitionCost() {
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    transferToTheRecipient(new BigDecimal("40.00000"), new BigDecimal("480.00"));

    assertThat(theOnlyRowOf(recipientPerson).acquisitionCost()).isEqualByComparingTo("480.00");
    assertThat(theOnlyRowOf(recipientPerson).amount()).isEqualByComparingTo("400.00");
  }

  @Test
  void anAcquisitionCostComesBackFromTheDatabaseInWholeCents() {
    givenTheGiverBought(new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    transferToTheRecipient(new BigDecimal("40.00000"), new BigDecimal("480.00"));
    flushAndForgetEverythingInMemory();

    assertThat(theOnlyRowOf(recipientPerson).acquisitionCost()).isEqualTo(new BigDecimal("480.00"));
  }

  private void flushAndForgetEverythingInMemory() {
    entityManager.flush();
    entityManager.clear();
  }

  private void transferToTheRecipient(BigDecimal units) {
    transferToTheRecipient(units, ZERO);
  }

  private void transferToTheRecipient(BigDecimal units, BigDecimal recipientAcquisitionCost) {
    clock.tick();
    savingsFundLedger.recordUnitTransfer(
        new UnitTransferInstruction(
            giver, recipient, units, recipientAcquisitionCost, randomUUID()));
  }

  private Transaction theOnlyRowOf(AuthenticatedPerson person) {
    List<Transaction> rows = transactions.getTransactions(person);
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  private Transaction theTransferRowOf(AuthenticatedPerson person) {
    List<Transaction> rows = transactions.getTransactions(person);
    return rows.stream()
        .filter(row -> row.amount().signum() < 0)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No transfer row: rows=" + rows));
  }

  private FundBalance statementOf(AuthenticatedPerson person) {
    return statements.getAccountStatement(person).orElseThrow();
  }

  private void navIs(BigDecimal nav) {
    given(navProvider.getDisplayNav(TKF100)).willReturn(nav);
  }

  private void givenTheGiverRedeemed(BigDecimal fundUnits, BigDecimal cashAmount) {
    var redemptionRequestId = randomUUID();
    var soldAt = cashAmount.divide(fundUnits, 5, HALF_UP);
    clock.tick();
    savingsFundLedger.reserveFundUnitsForRedemption(giver, fundUnits, redemptionRequestId);
    clock.tick();
    savingsFundLedger.redeemFundUnitsFromReserved(
        giver, fundUnits, cashAmount, soldAt, PRICED_ON, redemptionRequestId);
  }

  private void givenTheGiverBought(BigDecimal cashAmount, BigDecimal fundUnits) {
    var paymentId = randomUUID();
    var boughtAt = cashAmount.divide(fundUnits, 5, HALF_UP);
    clock.tick();
    savingsFundLedger.recordPaymentReceived(giver, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(giver, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        giver, cashAmount, fundUnits, boughtAt, PRICED_ON, paymentId);
  }

  private static AuthenticatedPerson person(String personalCode) {
    return AuthenticatedPerson.builder()
        .personalCode(personalCode)
        .firstName("Saver")
        .lastName("Of the savings fund")
        .role(new Role(RoleType.PERSON, personalCode, "Saver Of the savings fund"))
        .build();
  }
}
