package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.ACCOUNT_TAKEOVER_SIGNALS;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.HIGH_RISK;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.MANUAL;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.PEP;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SANCTION;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldService.SYSTEM;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.CONTACT_CHANGED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.LARGE_PAYOUT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.NEW_IBAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.banking.payment.OutgoingPaymentLookup;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class RedemptionHoldServiceTest {

  @Mock private RedemptionRequestRepository repository;
  @Mock private RedemptionStatusService redemptionStatusService;
  @Mock private RedemptionPayoutService payoutService;
  @Mock private RedemptionHoldNotifier notifier;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutgoingPaymentLookup outgoingPaymentLookup;

  @InjectMocks private RedemptionHoldService service;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void freeze_recordsTheHoldFreezesTheOrderAndNotifies() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(notifier.notifyFrozen(request)).willReturn(true);

    service.freeze(requestId);

    assertThat(request.getHoldReasons()).containsExactly(SANCTION);
    assertThat(request.getHoldAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getHeldBy()).isEqualTo(SYSTEM);
    assertThat(request.getHoldNotifiedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getHoldReleasedAt()).isNull();
    assertThat(request.hasActiveHold()).isTrue();
    verify(redemptionStatusService).changeStatus(requestId, FROZEN);
  }

  @Test
  void freeze_rejectsRequestThatIsNotReserved() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.freeze(requestId)).isInstanceOf(IllegalStateException.class);

    verify(redemptionStatusService, never()).changeStatus(any(), any());
    assertThat(request.getHoldReasons()).isEmpty();
  }

  @Test
  void holdPayout_recordsTheHoldAndNotifiesWithoutTouchingTheStatus() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(notifier.notifyPayoutHold(request)).willReturn(true);

    service.holdPayout(requestId, Set.of(PEP), Set.of());

    assertThat(request.getHoldReasons()).containsExactly(PEP);
    assertThat(request.getHoldAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getHeldBy()).isEqualTo(SYSTEM);
    assertThat(request.getHoldNotifiedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.hasActiveHold()).isTrue();
    verify(redemptionStatusService, never()).changeStatus(any(), any());
  }

  @Test
  void holdPayout_writesTheTakeoverSignalsIntoTheSystemHoldsCommentInAFixedOrder() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(notifier.notifyPayoutHold(request)).willReturn(true);

    service.holdPayout(
        requestId,
        Set.of(ACCOUNT_TAKEOVER_SIGNALS),
        Set.of(NEW_IBAN, CONTACT_CHANGED, LARGE_PAYOUT));

    assertThat(request.getHoldReasons()).containsExactly(ACCOUNT_TAKEOVER_SIGNALS);
    assertThat(request.getHoldComment())
        .isEqualTo("takeoverSignals=[LARGE_PAYOUT, NEW_IBAN, CONTACT_CHANGED]");
    assertThat(request.getHeldBy()).isEqualTo(SYSTEM);
  }

  @Test
  void holdPayout_namesASingleTakeoverSignalInTheCommentOfAHoldForAnotherReason() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.holdPayout(requestId, Set.of(HIGH_RISK), Set.of(LARGE_PAYOUT));

    assertThat(request.getHoldReasons()).containsExactly(HIGH_RISK);
    assertThat(request.getHoldComment()).isEqualTo("takeoverSignals=[LARGE_PAYOUT]");
  }

  @Test
  void holdPayout_leavesTheCommentOutWhenNoTakeoverSignalWasRaised() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.holdPayout(requestId, Set.of(PEP), Set.of());

    assertThat(request.getHoldComment()).isNull();
  }

  @Test
  void holdPayout_mergesTheTakeoverReasonIntoAnActiveManualHoldAndKeepsItsComment() {
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(RESERVED)
            .holdReasons(Set.of(MANUAL))
            .heldBy("AML Specialist")
            .holdComment("TKF volume alert")
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.holdPayout(requestId, Set.of(ACCOUNT_TAKEOVER_SIGNALS), Set.of(NEW_IBAN, LARGE_PAYOUT));

    assertThat(request.getHoldReasons())
        .containsExactlyInAnyOrder(MANUAL, ACCOUNT_TAKEOVER_SIGNALS);
    assertThat(request.getHoldComment()).isEqualTo("TKF volume alert");
    assertThat(request.getHeldBy()).isEqualTo("AML Specialist");
    verify(repository).save(request);
    verify(notifier, never()).notifyPayoutHold(any());
  }

  @Test
  void holdPayoutManually_refusesAHoldAttributedToTheSystem() {
    var requestId = UUID.randomUUID();

    assertThatThrownBy(() -> service.holdPayoutManually(requestId, SYSTEM, "TKF volume alert"))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(repository, notifier, redemptionStatusService);
  }

  @Test
  void holdPayout_keepsTheHoldButNotTheNotifiedTimestampWhenTheMessageDidNotGoOut() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(notifier.notifyPayoutHold(request)).willReturn(false);

    service.holdPayoutManually(requestId, "AML Specialist", "TKF volume alert");

    assertThat(request.hasActiveHold()).isTrue();
    assertThat(request.getHoldReasons()).containsExactly(MANUAL);
    assertThat(request.getHoldComment()).isEqualTo("TKF volume alert");
    assertThat(request.getHeldBy()).isEqualTo("AML Specialist");
    assertThat(request.getHoldNotifiedAt()).isNull();
  }

  @Test
  void holdPayout_addsAReasonToAnActiveHoldInsteadOfFailing() {
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(RESERVED)
            .holdReasons(Set.of(MANUAL))
            .heldBy("AML Specialist")
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.holdPayout(requestId, Set.of(PEP), Set.of());

    assertThat(request.getHoldReasons()).containsExactlyInAnyOrder(MANUAL, PEP);
    assertThat(request.getHeldBy()).isEqualTo("AML Specialist");
    verify(notifier, never()).notifyPayoutHold(any());
  }

  @Test
  void holdPayout_doesNotRepeatAReasonAlreadyOnTheHold() {
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture().id(requestId).status(VERIFIED).holdReasons(Set.of(PEP)).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.holdPayout(requestId, Set.of(PEP), Set.of());

    assertThat(request.getHoldReasons()).containsExactly(PEP);
    verify(repository, never()).save(any());
  }

  @Test
  void holdPayout_startsANewHoldAfterAnEarlierRelease() {
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(VERIFIED)
            .holdReasons(Set.of(PEP))
            .heldBy(SYSTEM)
            .holdNotifiedAt(TestClockHolder.now.minusSeconds(7200))
            .holdReleasedAt(TestClockHolder.now.minusSeconds(3600))
            .reviewedBy("AML Specialist")
            .reviewedAt(TestClockHolder.now.minusSeconds(3600))
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(notifier.notifyPayoutHold(request)).willReturn(true);

    service.holdPayoutManually(requestId, "Ops", "TKF volume alert");

    assertThat(request.hasActiveHold()).isTrue();
    assertThat(request.getHoldReasons()).containsExactly(MANUAL);
    assertThat(request.getHoldComment()).isEqualTo("TKF volume alert");
    assertThat(request.getHeldBy()).isEqualTo("Ops");
    assertThat(request.getHoldAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getHoldNotifiedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getHoldReleasedAt()).isNull();
    assertThat(request.getReviewedBy()).isEqualTo("AML Specialist");
  }

  @Test
  void holdPayout_takesAnAlreadyFundedRequestOutOfTheFundingQueueAtOnce() {
    var requestId = UUID.randomUUID();
    var batchId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(VERIFIED)
            .cashAmount(new java.math.BigDecimal("25.00"))
            .batchId(batchId)
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(outgoingPaymentLookup.findStatusForSource(batchId)).willReturn(Optional.of(SUBMITTED));
    given(notifier.notifyPayoutHold(request)).willReturn(true);

    service.holdPayoutManually(requestId, "AML Specialist", "TKF volume alert");

    verify(redemptionStatusService).changeStatus(requestId, PAYOUT_HELD);
  }

  @Test
  void holdPayout_leavesARequestWhoseBatchTransferTheBankRejectedForTheNextBatchToFund() {
    var requestId = UUID.randomUUID();
    var batchId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(VERIFIED)
            .cashAmount(new java.math.BigDecimal("25.00"))
            .batchId(batchId)
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(outgoingPaymentLookup.findStatusForSource(batchId))
        .willReturn(Optional.of(OutgoingPaymentStatus.FAILED));
    given(notifier.notifyPayoutHold(request)).willReturn(true);

    service.holdPayoutManually(requestId, "AML Specialist", "TKF volume alert");

    verify(redemptionStatusService, never()).changeStatus(any(), any());
    assertThat(request.hasActiveHold()).isTrue();
  }

  @Test
  void holdPayout_leavesARequestWhoseBatchTransferWasNeverRecordedForTheNextBatchToFund() {
    var requestId = UUID.randomUUID();
    var batchId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(VERIFIED)
            .cashAmount(new java.math.BigDecimal("25.00"))
            .batchId(batchId)
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(outgoingPaymentLookup.findStatusForSource(batchId)).willReturn(Optional.empty());
    given(notifier.notifyPayoutHold(request)).willReturn(true);

    service.holdPayoutManually(requestId, "AML Specialist", "TKF volume alert");

    verify(redemptionStatusService, never()).changeStatus(any(), any());
    assertThat(request.hasActiveHold()).isTrue();
  }

  @Test
  void holdPayout_leavesAPricedRequestWhoseTransferNeverWentOutForTheNextBatchToFund() {
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(VERIFIED)
            .cashAmount(new java.math.BigDecimal("25.00"))
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(notifier.notifyPayoutHold(request)).willReturn(true);

    service.holdPayoutManually(requestId, "AML Specialist", "TKF volume alert");

    verify(redemptionStatusService, never()).changeStatus(any(), any());
    assertThat(request.hasActiveHold()).isTrue();
  }

  @Test
  void holdPayout_leavesAnUnpricedRequestForTheBatchToPrice() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(notifier.notifyPayoutHold(request)).willReturn(true);

    service.holdPayoutManually(requestId, "AML Specialist", "TKF volume alert");

    verify(redemptionStatusService, never()).changeStatus(any(), any());
  }

  @Test
  void holdPayout_rejectsRequestWhosePayoutIsAlreadySent() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(REDEEMED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.holdPayoutManually(requestId, "AML Specialist", "too late"))
        .isInstanceOf(IllegalStateException.class);

    assertThat(request.getHoldReasons()).isEmpty();
  }

  @Test
  void release_frozenOrder_recordsTheReviewAndPutsItBackInTheQueue() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(FROZEN)
            .holdReasons(Set.of(SANCTION))
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.release(requestId, "Contact person", "False positive, namesake");

    assertThat(request.getReviewedBy()).isEqualTo("Contact person");
    assertThat(request.getReviewReason()).isEqualTo("False positive, namesake");
    assertThat(request.getReviewedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getHoldReleasedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getRequeuedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.hasActiveHold()).isFalse();
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verify(notifier).notifyReleased(requestId);
    verify(payoutService, never()).payOutHeld(any());
  }

  @Test
  void release_flaggedVerifiedRequest_clearsTheHoldWithoutChangingTheStatusOrDealingDay() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture().id(requestId).status(VERIFIED).holdReasons(Set.of(PEP)).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.release(requestId, "AML Specialist", "Source of funds confirmed");

    assertThat(request.hasActiveHold()).isFalse();
    assertThat(request.getReviewedBy()).isEqualTo("AML Specialist");
    assertThat(request.getHoldReleasedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getRequeuedAt()).isNull();
    verify(redemptionStatusService, never()).changeStatus(any(), any());
    verify(payoutService, never()).payOutHeld(any());
    verify(notifier).notifyReleased(requestId);
  }

  @Test
  void release_heldPayout_recordsTheReviewThenPaysOut() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(PAYOUT_HELD)
            .holdReasons(Set.of(PEP))
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.release(requestId, "AML Specialist", "Source of funds confirmed");

    assertThat(request.hasActiveHold()).isFalse();
    assertThat(request.getReviewedAt()).isEqualTo(TestClockHolder.now);
    verify(payoutService).payOutHeld(requestId);
    verify(redemptionStatusService, never()).changeStatus(any(), any());
  }

  @Test
  void release_heldPayoutWhoseHoldWasReleasedDuringTheBatch_stillPaysOut() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(PAYOUT_HELD)
            .holdReasons(Set.of(PEP))
            .holdReleasedAt(TestClockHolder.now.minusSeconds(60))
            .reviewedBy("AML Specialist")
            .reviewReason("Source of funds confirmed")
            .reviewedAt(TestClockHolder.now.minusSeconds(60))
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.release(requestId, "Ops", "second attempt");

    assertThat(request.getReviewedBy()).isEqualTo("AML Specialist");
    assertThat(request.getReviewedAt()).isEqualTo(TestClockHolder.now.minusSeconds(60));
    verify(payoutService).payOutHeld(requestId);
  }

  @Test
  void release_failedPayoutUnderHold_clearsTheHoldSoTheAdminRetryCanSendIt() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(FAILED)
            .cashAmount(new java.math.BigDecimal("25.00"))
            .holdReasons(Set.of(PEP))
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.release(requestId, "AML Specialist", "Source of funds confirmed");

    assertThat(request.hasActiveHold()).isFalse();
    assertThat(request.getStatus()).isEqualTo(FAILED);
    verify(payoutService, never()).payOutHeld(any());
    verify(redemptionStatusService, never()).changeStatus(any(), any());
  }

  @Test
  void release_rejectsFailedRequestWithoutAnActiveHold() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(FAILED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.release(requestId, "AML Specialist", "reason"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void release_reportsFailureWhenTheReleasedPayoutCouldNotBeSent() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(PAYOUT_HELD)
            .holdReasons(Set.of(PEP))
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(payoutService.payOutHeld(requestId))
        .willReturn(RedemptionPayoutService.Outcome.FAILED_TO_SEND);

    assertThatThrownBy(() -> service.release(requestId, "AML Specialist", "confirmed"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void release_rejectsVerifiedRequestWithoutAnActiveHold() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.release(requestId, "AML Specialist", "reason"))
        .isInstanceOf(IllegalStateException.class);

    verify(notifier, never()).notifyReleased(any());
    verify(payoutService, never()).payOutHeld(any());
  }

  @Test
  void release_rejectsRequestWhosePayoutIsAlreadySent() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture().id(requestId).status(REDEEMED).holdReasons(Set.of(PEP)).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.release(requestId, "AML Specialist", "reason"))
        .isInstanceOf(IllegalStateException.class);

    assertThat(request.getReviewedAt()).isNull();
  }

  @Test
  void release_throwsWhenRedemptionNotFound() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.release(requestId, "AML Specialist", "reason"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resendUnsentHoldNotifications_marksOnlyTheDeliveredOnesWithoutRewritingTheRow() {
    var frozen =
        redemptionRequestFixture()
            .id(UUID.randomUUID())
            .status(FROZEN)
            .holdReasons(Set.of(SANCTION))
            .build();
    var held =
        redemptionRequestFixture()
            .id(UUID.randomUUID())
            .status(PAYOUT_HELD)
            .holdReasons(Set.of(PEP))
            .build();
    given(
            repository.findWithUnsentHoldNotification(
                List.of(RESERVED, FROZEN, VERIFIED, PAYOUT_HELD)))
        .willReturn(List.of(frozen, held));
    given(notifier.notifyHold(frozen)).willReturn(true);
    given(notifier.notifyHold(held)).willReturn(false);

    service.resendUnsentHoldNotifications();

    verify(repository).markHoldNotified(frozen.getId(), TestClockHolder.now);
    verify(repository, never()).markHoldNotified(eq(held.getId()), any());
    verify(repository, never()).save(any());
  }

  private void runTransactionsInline() {
    doAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());
  }
}
