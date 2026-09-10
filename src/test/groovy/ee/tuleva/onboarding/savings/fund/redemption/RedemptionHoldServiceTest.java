package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldService.SYSTEM;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.util.List;
import java.util.Optional;
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

    service.freeze(requestId, "SANCTION");

    assertThat(request.getHoldReason()).isEqualTo("SANCTION");
    assertThat(request.getHoldAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getHeldBy()).isEqualTo(SYSTEM);
    assertThat(request.getHoldNotifiedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.hasActiveHold()).isTrue();
    verify(redemptionStatusService).changeStatus(requestId, FROZEN);
  }

  @Test
  void freeze_rejectsRequestThatIsNotReserved() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.freeze(requestId, "SANCTION"))
        .isInstanceOf(IllegalStateException.class);

    verify(redemptionStatusService, never()).changeStatus(any(), any());
    assertThat(request.getHoldReason()).isNull();
  }

  @Test
  void holdPayout_recordsTheHoldAndNotifiesWithoutTouchingTheStatus() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(notifier.notifyPayoutHold(request)).willReturn(true);

    service.holdPayout(requestId, "PEP", SYSTEM);

    assertThat(request.getHoldReason()).isEqualTo("PEP");
    assertThat(request.getHoldAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.getHeldBy()).isEqualTo(SYSTEM);
    assertThat(request.getHoldNotifiedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.hasActiveHold()).isTrue();
    verify(redemptionStatusService, never()).changeStatus(any(), any());
  }

  @Test
  void holdPayout_keepsTheHoldButNotTheNotifiedTimestampWhenTheMessageDidNotGoOut() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));
    given(notifier.notifyPayoutHold(request)).willReturn(false);

    service.holdPayout(requestId, "MANUAL: volume alert", "AML Specialist");

    assertThat(request.hasActiveHold()).isTrue();
    assertThat(request.getHeldBy()).isEqualTo("AML Specialist");
    assertThat(request.getHoldNotifiedAt()).isNull();
  }

  @Test
  void holdPayout_rejectsRequestThatIsAlreadyOnHold() {
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture().id(requestId).status(VERIFIED).holdReason("PEP").build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.holdPayout(requestId, "HIGH_RISK", SYSTEM))
        .isInstanceOf(IllegalStateException.class);

    assertThat(request.getHoldReason()).isEqualTo("PEP");
  }

  @Test
  void holdPayout_rejectsRequestThatWasAlreadyReleasedOnce() {
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(VERIFIED)
            .holdReason("PEP")
            .reviewedAt(TestClockHolder.now)
            .build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.holdPayout(requestId, "MANUAL: again", "AML Specialist"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void holdPayout_rejectsRequestWhosePayoutIsAlreadySent() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(REDEEMED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.holdPayout(requestId, "MANUAL: too late", "AML Specialist"))
        .isInstanceOf(IllegalStateException.class);

    assertThat(request.getHoldReason()).isNull();
  }

  @Test
  void release_frozenOrder_recordsTheReviewAndPutsItBackInTheQueue() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture().id(requestId).status(FROZEN).holdReason("SANCTION").build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.release(requestId, "Contact person", "False positive, namesake");

    assertThat(request.getReviewedBy()).isEqualTo("Contact person");
    assertThat(request.getReviewReason()).isEqualTo("False positive, namesake");
    assertThat(request.getReviewedAt()).isEqualTo(TestClockHolder.now);
    assertThat(request.hasActiveHold()).isFalse();
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verify(notifier).notifyReleased(requestId, "Contact person");
    verify(payoutService, never()).payOutHeld(any());
  }

  @Test
  void release_flaggedVerifiedRequest_clearsTheHoldWithoutChangingTheStatus() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture().id(requestId).status(VERIFIED).holdReason("PEP").build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.release(requestId, "AML Specialist", "Source of funds confirmed");

    assertThat(request.hasActiveHold()).isFalse();
    assertThat(request.getReviewedBy()).isEqualTo("AML Specialist");
    verify(redemptionStatusService, never()).changeStatus(any(), any());
    verify(payoutService, never()).payOutHeld(any());
    verify(notifier).notifyReleased(requestId, "AML Specialist");
  }

  @Test
  void release_heldPayout_recordsTheReviewThenPaysOut() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture().id(requestId).status(PAYOUT_HELD).holdReason("PEP").build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.release(requestId, "AML Specialist", "Source of funds confirmed");

    assertThat(request.hasActiveHold()).isFalse();
    assertThat(request.getReviewedAt()).isEqualTo(TestClockHolder.now);
    verify(payoutService).payOutHeld(requestId);
    verify(redemptionStatusService, never()).changeStatus(any(), any());
  }

  @Test
  void release_rejectsVerifiedRequestWithoutAnActiveHold() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.release(requestId, "AML Specialist", "reason"))
        .isInstanceOf(IllegalStateException.class);

    verify(notifier, never()).notifyReleased(any(), any());
    verify(payoutService, never()).payOutHeld(any());
  }

  @Test
  void release_rejectsRequestWhosePayoutIsAlreadySent() {
    runTransactionsInline();
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture().id(requestId).status(REDEEMED).holdReason("PEP").build();
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
  void resendUnsentHoldNotifications_marksOnlyTheDeliveredOnes() {
    var frozen =
        redemptionRequestFixture()
            .id(UUID.randomUUID())
            .status(FROZEN)
            .holdReason("SANCTION")
            .build();
    var held =
        redemptionRequestFixture()
            .id(UUID.randomUUID())
            .status(PAYOUT_HELD)
            .holdReason("PEP")
            .build();
    given(repository.findWithUnsentHoldNotification(List.of(FROZEN, VERIFIED, PAYOUT_HELD)))
        .willReturn(List.of(frozen, held));
    given(notifier.notifyHold(frozen)).willReturn(true);
    given(notifier.notifyHold(held)).willReturn(false);

    service.resendUnsentHoldNotifications();

    assertThat(frozen.getHoldNotifiedAt()).isEqualTo(TestClockHolder.now);
    assertThat(held.getHoldNotifiedAt()).isNull();
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
