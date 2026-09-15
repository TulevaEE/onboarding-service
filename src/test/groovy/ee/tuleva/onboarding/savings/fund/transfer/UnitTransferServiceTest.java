package ee.tuleva.onboarding.savings.fund.transfer;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_TRANSFER;
import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.AWAITING_APPROVAL;
import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.CANCELLED;
import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.EXECUTED;
import static java.math.BigDecimal.ZERO;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.UnitTransferQuote;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Planned;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Refused;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnitTransferServiceTest {

  SavingsFundLedger savingsFundLedger = mock(SavingsFundLedger.class);
  SavingsFundOnboardingService onboarding = mock(SavingsFundOnboardingService.class);
  UnitTransferRepository transfers = mock(UnitTransferRepository.class);
  Clock clock = Clock.fixed(Instant.parse("2026-09-15T09:00:00Z"), ZoneOffset.UTC);

  UnitTransferService service =
      new UnitTransferService(savingsFundLedger, onboarding, transfers, clock);

  UnitTransferCommand command =
      new UnitTransferCommand(
          "38888888888",
          PERSON,
          "39999999999",
          PERSON,
          new BigDecimal("40.00000"),
          LocalDate.parse("2026-09-14"),
          "Notice by email from the owner, 2026-09-14",
          ZERO);

  @BeforeEach
  void recipientIsOnboarded() {
    given(onboarding.isOnboardingCompleted("39999999999", PartyId.Type.PERSON)).willReturn(true);
  }

  @Test
  void previewAnswersWhatTheTransferWouldDo() {
    givenTheLedgerQuotes();

    var verdict = service.preview(command);

    assertThat(verdict).isInstanceOf(Planned.class);
    var planned = (Planned) verdict;
    assertThat(planned.plan().fundUnits()).isEqualByComparingTo("40.00000");
    assertThat(planned.plan().giverUnitsAfter()).isEqualByComparingTo("60.00000");
    assertThat(planned.planHash()).isNotBlank();
  }

  @Test
  void previewRefusesARecipientWhoHasNotCompletedOnboarding() {
    given(onboarding.isOnboardingCompleted("39999999999", PartyId.Type.PERSON)).willReturn(false);

    var verdict = service.preview(command);

    assertThat(verdict).isInstanceOf(Refused.class);
    assertThat(((Refused) verdict).refused()).contains("not completed savings fund onboarding");
    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void previewRefusesWhatTheLedgerWillNotDo() {
    given(savingsFundLedger.quoteUnitTransfer(any(), any(), any()))
        .willThrow(new IllegalStateException("Cannot transfer more units than the party holds"));

    var verdict = service.preview(command);

    assertThat(((Refused) verdict).refused()).contains("more units than the party holds");
  }

  @Test
  void theSamePlanHashesTheSameWay() {
    givenTheLedgerQuotes();

    var first = (Planned) service.preview(command);
    var second = (Planned) service.preview(command);

    assertThat(second.planHash()).isEqualTo(first.planHash());
  }

  @Test
  void changingTheUnitsChangesThePlanHash() {
    givenTheLedgerQuotes();
    var planned = (Planned) service.preview(command);

    given(savingsFundLedger.quoteUnitTransfer(any(), any(), any()))
        .willReturn(
            new UnitTransferQuote(
                new BigDecimal("41.00000"),
                new BigDecimal("59.00000"),
                new BigDecimal("41.00000"),
                new BigDecimal("1000.00"),
                new BigDecimal("100.00000")));
    var other = (Planned) service.preview(command);

    assertThat(other.planHash()).isNotEqualTo(planned.planHash());
  }

  @Test
  void submittingWithoutThePlanHashIsRefused() {
    givenTheLedgerQuotes();

    assertThatThrownBy(() -> service.submit(command, "not-the-hash", "operator@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Confirm does not match the plan");
    verifyNoInteractions(transfers);
  }

  @Test
  void submittingRecordsTheTransferWithoutMovingUnits() {
    givenTheLedgerQuotes();
    var planned = (Planned) service.preview(command);
    givenTheRepositoryReturnsWhateverItIsGiven();

    var submitted = service.submit(command, planned.planHash(), "operator@example.com");

    assertThat(submitted.isAwaitingApproval()).isTrue();
    assertThat(submitted.getSubmittedBy()).isEqualTo("operator@example.com");
    assertThat(submitted.getPlanHash()).isEqualTo(planned.planHash());
    assertThat(submitted.getRecipientAcquisitionCostEur()).isEqualByComparingTo(ZERO);
  }

  @Test
  void theGiversOwnFiguresAreShownSoAnOperatorCanDecideWhatTheRecipientMayCount() {
    givenTheLedgerQuotes();

    var planned = (Planned) service.preview(command);

    assertThat(planned.plan().giverPaidIn()).isEqualByComparingTo("1000.00");
    assertThat(planned.plan().giverUnitsOwned()).isEqualByComparingTo("100.00000");
  }

  @Test
  void submittingKeepsTheGiversFiguresAsTheyWereShown() {
    givenTheLedgerQuotes();
    var planned = (Planned) service.preview(command);
    givenTheRepositoryReturnsWhateverItIsGiven();

    var submitted = service.submit(command, planned.planHash(), "operator@example.com");

    assertThat(submitted.getGiverPaidInEur()).isEqualByComparingTo(planned.plan().giverPaidIn());
    assertThat(submitted.getGiverUnitsOwned())
        .isEqualByComparingTo(planned.plan().giverUnitsOwned());
  }

  @Test
  void changingWhatTheGiverPaidInChangesThePlanHash() {
    givenTheLedgerQuotes();
    var planned = (Planned) service.preview(command);

    given(savingsFundLedger.quoteUnitTransfer(any(), any(), any()))
        .willReturn(
            new UnitTransferQuote(
                new BigDecimal("40.00000"),
                new BigDecimal("60.00000"),
                new BigDecimal("40.00000"),
                new BigDecimal("1500.00"),
                new BigDecimal("100.00000")));
    var other = (Planned) service.preview(command);

    assertThat(other.planHash()).isNotEqualTo(planned.planHash());
  }

  @Test
  void changingHowManyUnitsTheGiverOwnsChangesThePlanHash() {
    givenTheLedgerQuotes();
    var planned = (Planned) service.preview(command);

    given(savingsFundLedger.quoteUnitTransfer(any(), any(), any()))
        .willReturn(
            new UnitTransferQuote(
                new BigDecimal("40.00000"),
                new BigDecimal("60.00000"),
                new BigDecimal("40.00000"),
                new BigDecimal("1000.00"),
                new BigDecimal("120.00000")));
    var other = (Planned) service.preview(command);

    assertThat(other.planHash()).isNotEqualTo(planned.planHash());
  }

  @Test
  void aSpouseInheritingJointPropertyIsRecordedWithAnAcquisitionCostOfTheirOwn() {
    givenTheLedgerQuotes();
    var spouseCountsTheWholeCost =
        new UnitTransferCommand(
            "38888888888",
            PERSON,
            "39999999999",
            PERSON,
            new BigDecimal("40.00000"),
            LocalDate.parse("2026-09-14"),
            "Notice by email, joint marital property",
            new BigDecimal("1000.00"));
    var planned = (Planned) service.preview(spouseCountsTheWholeCost);
    givenTheRepositoryReturnsWhateverItIsGiven();

    var submitted =
        service.submit(spouseCountsTheWholeCost, planned.planHash(), "operator@example.com");

    assertThat(submitted.getRecipientAcquisitionCostEur()).isEqualByComparingTo("1000.00");
  }

  @Test
  void submittingTheSameTransferTwiceLeavesOneWaitingForApproval() {
    givenTheLedgerQuotes();
    var planned = (Planned) service.preview(command);
    givenTheRepositoryReturnsWhateverItIsGiven();
    var first = service.submit(command, planned.planHash(), "operator@example.com");
    given(transfers.findByPlanHashAndState(planned.planHash(), AWAITING_APPROVAL))
        .willReturn(Optional.of(first));

    var second = service.submit(command, planned.planHash(), "operator@example.com");

    assertThat(second).isSameAs(first);
  }

  @Test
  void approvingByWhoeverSubmittedIsRefused() {
    var awaiting = anAwaitingTransfer();
    given(transfers.findByIdForUpdate(awaiting.getId())).willReturn(Optional.of(awaiting));

    assertThatThrownBy(() -> service.approve(awaiting.getId(), "operator@example.com"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("approved by someone other than whoever submitted it");
    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void approvingBySomeoneElseMovesTheUnits() {
    var awaiting = anAwaitingTransfer();
    given(transfers.findByIdForUpdate(awaiting.getId())).willReturn(Optional.of(awaiting));
    givenTheRepositoryReturnsWhateverItIsGiven();
    UUID ledgerTransactionId = randomUUID();
    var recorded =
        LedgerTransaction.builder()
            .id(ledgerTransactionId)
            .transactionType(UNIT_TRANSFER)
            .transactionDate(Instant.parse("2026-09-15T09:00:00Z"))
            .build();
    given(
            savingsFundLedger.recordUnitTransfer(
                any(PartyRef.class), any(PartyRef.class), any(), any()))
        .willReturn(recorded);

    var approved = service.approve(awaiting.getId(), "approver@example.com");

    assertThat(approved.getState()).isEqualTo(EXECUTED);
    assertThat(approved.getApprovedBy()).isEqualTo("approver@example.com");
    assertThat(approved.getLedgerTransactionId()).isEqualTo(ledgerTransactionId);
    assertThat(approved.getExecutedAt()).isEqualTo(Instant.parse("2026-09-15T09:00:00Z"));
  }

  @Test
  void approvingAsYourselfSpeltDifferentlyIsStillRefused() {
    var awaiting = anAwaitingTransfer();
    given(transfers.findByIdForUpdate(awaiting.getId())).willReturn(Optional.of(awaiting));

    assertThatThrownBy(() -> service.approve(awaiting.getId(), "  OPERATOR@Example.com "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("approved by someone other than whoever submitted it");
    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void anUnnamedApproverIsRefused() {
    var awaiting = anAwaitingTransfer();
    given(transfers.findByIdForUpdate(awaiting.getId())).willReturn(Optional.of(awaiting));
    givenTheLedgerRecords();

    assertThatThrownBy(() -> service.approve(awaiting.getId(), "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must name who is approving it");
  }

  @Test
  void evidenceCannotBeWrittenToImpersonateTheFieldsAroundIt() {
    givenTheLedgerQuotes();
    var withAPipe = commandWithEvidence("Notice by email|0");
    var withoutIt = commandWithEvidence("Notice by email");

    var one = (Planned) service.preview(withAPipe);
    var other = (Planned) service.preview(withoutIt);

    assertThat(one.planHash()).isNotEqualTo(other.planHash());
  }

  @Test
  void anAlreadyExecutedTransferCannotBeApprovedAgain() {
    var awaiting = anAwaitingTransfer();
    awaiting.executedBy(
        "approver@example.com", randomUUID(), Instant.parse("2026-09-15T08:00:00Z"));
    given(transfers.findByIdForUpdate(awaiting.getId())).willReturn(Optional.of(awaiting));

    assertThatThrownBy(() -> service.approve(awaiting.getId(), "someone-else@example.com"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only a transfer awaiting approval can be approved");
    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void cancellingLeavesTheUnitsAlone() {
    var awaiting = anAwaitingTransfer();
    given(transfers.findByIdForUpdate(awaiting.getId())).willReturn(Optional.of(awaiting));
    givenTheRepositoryReturnsWhateverItIsGiven();

    var cancelled = service.cancel(awaiting.getId());

    assertThat(cancelled.getState()).isEqualTo(CANCELLED);
    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void awaitingApprovalListsWhatStillNeedsASecondPerson() {
    var awaiting = anAwaitingTransfer();
    given(transfers.findAllByStateOrderByCreatedAtDesc(UnitTransferState.AWAITING_APPROVAL))
        .willReturn(List.of(awaiting));

    assertThat(service.awaitingApproval()).containsExactly(awaiting);
  }

  private UnitTransferCommand commandWithEvidence(String evidence) {
    return new UnitTransferCommand(
        "38888888888",
        PERSON,
        "39999999999",
        PERSON,
        new BigDecimal("40.00000"),
        LocalDate.parse("2026-09-14"),
        evidence,
        ZERO);
  }

  private void givenTheLedgerRecords() {
    given(
            savingsFundLedger.recordUnitTransfer(
                any(PartyRef.class), any(PartyRef.class), any(), any()))
        .willReturn(
            LedgerTransaction.builder()
                .id(randomUUID())
                .transactionType(UNIT_TRANSFER)
                .transactionDate(Instant.parse("2026-09-15T09:00:00Z"))
                .build());
  }

  private void givenTheLedgerQuotes() {
    given(savingsFundLedger.quoteUnitTransfer(any(), any(), any()))
        .willReturn(
            new UnitTransferQuote(
                new BigDecimal("40.00000"),
                new BigDecimal("60.00000"),
                new BigDecimal("40.00000"),
                new BigDecimal("1000.00"),
                new BigDecimal("100.00000")));
  }

  private void givenTheRepositoryReturnsWhateverItIsGiven() {
    given(transfers.save(any(UnitTransfer.class))).willAnswer(saved -> saved.getArgument(0));
  }

  private UnitTransfer anAwaitingTransfer() {
    return UnitTransfer.builder()
        .id(randomUUID())
        .fromPartyCode("38888888888")
        .fromPartyType(PERSON)
        .toPartyCode("39999999999")
        .toPartyType(PERSON)
        .fundUnits(new BigDecimal("40.00000"))
        .notifiedAt(LocalDate.parse("2026-09-14"))
        .evidence("Notice by email from the owner, 2026-09-14")
        .giverPaidInEur(new BigDecimal("1000.00"))
        .giverUnitsOwned(new BigDecimal("100.00000"))
        .planHash("whatever-was-previewed")
        .state(UnitTransferState.AWAITING_APPROVAL)
        .submittedBy("operator@example.com")
        .build();
  }
}
