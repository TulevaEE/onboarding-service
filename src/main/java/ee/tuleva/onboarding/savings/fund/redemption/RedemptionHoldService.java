package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.MANUAL;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SANCTION;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionHoldService {

  static final String SYSTEM = "SYSTEM";

  private static final List<Status> HOLDABLE_STATUSES =
      List.of(RESERVED, FROZEN, VERIFIED, PAYOUT_HELD);

  private final RedemptionRequestRepository repository;
  private final RedemptionStatusService redemptionStatusService;
  private final RedemptionPayoutService payoutService;
  private final RedemptionHoldNotifier notifier;
  private final TransactionTemplate transactionTemplate;

  @Transactional
  public void freeze(UUID id) {
    RedemptionRequest request = findForUpdate(id);
    if (request.getStatus() != RESERVED) {
      throw new IllegalStateException(
          "Only reserved redemptions can be frozen: id=" + id + ", status=" + request.getStatus());
    }
    startHold(request, Set.of(SANCTION), SYSTEM, null);
    redemptionStatusService.changeStatus(id, FROZEN);
    if (notifier.notifyFrozen(request)) {
      markNotified(request);
    }
    log.info("Redemption frozen: id={}, reason={}", id, SANCTION);
  }

  @Transactional
  public void holdPayout(UUID id, Set<RedemptionHoldReason> reasons) {
    holdPayout(id, reasons, SYSTEM, null);
  }

  @Transactional
  public void holdPayoutManually(UUID id, String by, String comment) {
    holdPayout(id, Set.of(MANUAL), by, comment);
  }

  private void holdPayout(
      UUID id, Set<RedemptionHoldReason> reasons, String by, @Nullable String comment) {
    RedemptionRequest request = findForUpdate(id);
    if (request.getStatus() != RESERVED && request.getStatus() != VERIFIED) {
      throw new IllegalStateException(
          "Payout can be held only before it is sent: id="
              + id
              + ", status="
              + request.getStatus());
    }
    if (request.hasActiveHold()) {
      addHoldReasons(request, reasons);
      return;
    }
    startHold(request, reasons, by, comment);
    if (notifier.notifyPayoutHold(request)) {
      markNotified(request);
    }
    log.info("Redemption payout held: id={}, reasons={}, by={}", id, reasons, by);
  }

  // SebPaymentRequestListener is a plain @EventListener, so the payout event must be published
  // after the transaction that records the release has committed.
  public void release(UUID id, String by, String reason) {
    var releasedFrom = transactionTemplate.execute(tx -> recordRelease(id, by, reason));
    notifier.notifyReleased(id);
    if (releasedFrom == PAYOUT_HELD) {
      payoutService.payOutHeld(id);
    }
  }

  private Status recordRelease(UUID id, String by, String reason) {
    RedemptionRequest request = findForUpdate(id);
    Status status = request.getStatus();
    switch (status) {
      case FROZEN -> {
        request.setRequeuedAt(clock().instant());
        recordReview(request, by, reason);
        redemptionStatusService.changeStatus(id, VERIFIED);
      }
      case VERIFIED -> {
        if (!request.hasActiveHold()) {
          throw new IllegalStateException(
              "Nothing to release: id=" + id + ", status=" + status + ", no active hold");
        }
        recordReview(request, by, reason);
      }
      case PAYOUT_HELD -> {
        // A release that raced the batch job between pricing and holding is still paid out.
        if (request.hasActiveHold()) {
          recordReview(request, by, reason);
        }
      }
      default ->
          throw new IllegalStateException(
              "Cannot release redemption: id=" + id + ", status=" + status);
    }
    log.info("Redemption released: id={}, from={}, by={}", id, status, by);
    return status;
  }

  public void resendUnsentHoldNotifications() {
    for (RedemptionRequest request : repository.findWithUnsentHoldNotification(HOLDABLE_STATUSES)) {
      if (notifier.notifyHold(request)) {
        repository.markHoldNotified(request.getId(), clock().instant());
      }
    }
  }

  private void addHoldReasons(RedemptionRequest request, Set<RedemptionHoldReason> reasons) {
    Set<RedemptionHoldReason> merged = EnumSet.noneOf(RedemptionHoldReason.class);
    merged.addAll(request.getHoldReasons());
    if (merged.addAll(reasons)) {
      request.setHoldReasons(merged);
      repository.save(request);
    }
    log.info(
        "Redemption already on hold, reasons merged: id={}, reasons={}",
        request.getId(),
        request.getHoldReasons());
  }

  private void startHold(
      RedemptionRequest request,
      Set<RedemptionHoldReason> reasons,
      String by,
      @Nullable String comment) {
    request.setHoldReasons(reasons);
    request.setHoldComment(comment);
    request.setHoldAt(clock().instant());
    request.setHeldBy(by);
    request.setHoldNotifiedAt(null);
    request.setHoldReleasedAt(null);
    repository.save(request);
  }

  private void recordReview(RedemptionRequest request, String by, String reason) {
    request.setReviewedBy(by);
    request.setReviewReason(reason);
    request.setReviewedAt(clock().instant());
    request.setHoldReleasedAt(clock().instant());
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
