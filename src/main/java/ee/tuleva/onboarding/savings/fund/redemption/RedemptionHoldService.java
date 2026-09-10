package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionHoldService {

  static final String SYSTEM = "SYSTEM";

  private static final List<Status> HOLDABLE_STATUSES = List.of(FROZEN, VERIFIED, PAYOUT_HELD);

  private final RedemptionRequestRepository repository;
  private final RedemptionStatusService redemptionStatusService;
  private final RedemptionPayoutService payoutService;
  private final RedemptionHoldNotifier notifier;
  private final TransactionTemplate transactionTemplate;

  @Transactional
  public void freeze(UUID id, String reason) {
    RedemptionRequest request = findForUpdate(id);
    if (request.getStatus() != RESERVED) {
      throw new IllegalStateException(
          "Only reserved redemptions can be frozen: id=" + id + ", status=" + request.getStatus());
    }
    recordHold(request, reason, SYSTEM);
    redemptionStatusService.changeStatus(id, FROZEN);
    if (notifier.notifyFrozen(request)) {
      markNotified(request);
    }
    log.info("Redemption frozen: id={}, reason={}", id, reason);
  }

  @Transactional
  public void holdPayout(UUID id, String reason, String by) {
    RedemptionRequest request = findForUpdate(id);
    if (request.getStatus() != RESERVED && request.getStatus() != VERIFIED) {
      throw new IllegalStateException(
          "Payout can be held only before it is sent: id="
              + id
              + ", status="
              + request.getStatus());
    }
    if (request.getReviewedAt() != null) {
      throw new IllegalStateException("Redemption was already released once: id=" + id);
    }
    if (request.getHoldReason() != null) {
      throw new IllegalStateException(
          "Redemption is already on hold: id=" + id + ", reason=" + request.getHoldReason());
    }
    recordHold(request, reason, by);
    if (notifier.notifyPayoutHold(request)) {
      markNotified(request);
    }
    log.info("Redemption payout held: id={}, reason={}, by={}", id, reason, by);
  }

  // SebPaymentRequestListener is a plain @EventListener, so the payout event must be published
  // after the transaction that records the release has committed.
  public void release(UUID id, String by, String reason) {
    var releasedFrom = transactionTemplate.execute(tx -> recordRelease(id, by, reason));
    notifier.notifyReleased(id, by);
    if (releasedFrom == PAYOUT_HELD) {
      payoutService.payOutHeld(id);
    }
  }

  private Status recordRelease(UUID id, String by, String reason) {
    RedemptionRequest request = findForUpdate(id);
    Status status = request.getStatus();
    switch (status) {
      case FROZEN -> {
        recordReview(request, by, reason);
        redemptionStatusService.changeStatus(id, VERIFIED);
      }
      case VERIFIED, PAYOUT_HELD -> {
        if (!request.hasActiveHold()) {
          throw new IllegalStateException(
              "Nothing to release: id=" + id + ", status=" + status + ", no active hold");
        }
        recordReview(request, by, reason);
      }
      default ->
          throw new IllegalStateException(
              "Cannot release redemption: id=" + id + ", status=" + status);
    }
    log.info("Redemption released: id={}, from={}, by={}", id, status, by);
    return status;
  }

  @Transactional
  public void resendUnsentHoldNotifications() {
    for (RedemptionRequest request : repository.findWithUnsentHoldNotification(HOLDABLE_STATUSES)) {
      if (notifier.notifyHold(request)) {
        markNotified(request);
      }
    }
  }

  private void recordHold(RedemptionRequest request, String reason, String by) {
    request.setHoldReason(reason);
    request.setHoldAt(clock().instant());
    request.setHeldBy(by);
    repository.save(request);
  }

  private void recordReview(RedemptionRequest request, String by, String reason) {
    request.setReviewedBy(by);
    request.setReviewReason(reason);
    request.setReviewedAt(clock().instant());
    repository.save(request);
  }

  private void markNotified(RedemptionRequest request) {
    request.setHoldNotifiedAt(clock().instant());
    repository.save(request);
  }

  private RedemptionRequest findForUpdate(UUID id) {
    return repository
        .findByIdForUpdate(id)
        .orElseThrow(() -> new IllegalArgumentException("Redemption request not found: id=" + id));
  }
}
