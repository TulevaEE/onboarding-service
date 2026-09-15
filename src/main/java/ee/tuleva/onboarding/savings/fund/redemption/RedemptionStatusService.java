package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionStatusService {

  private static final Set<StatusTransition> ALLOWED_TRANSITIONS =
      Set.of(
          new StatusTransition(RESERVED, FROZEN),
          new StatusTransition(RESERVED, VERIFIED),
          new StatusTransition(RESERVED, CANCELLED),
          new StatusTransition(RESERVED, FAILED),
          new StatusTransition(FROZEN, VERIFIED),
          new StatusTransition(VERIFIED, PAYOUT_HELD),
          new StatusTransition(VERIFIED, CANCELLED),
          new StatusTransition(VERIFIED, REDEEMED),
          new StatusTransition(VERIFIED, FAILED),
          new StatusTransition(PAYOUT_HELD, REDEEMED),
          new StatusTransition(PAYOUT_HELD, FAILED),
          new StatusTransition(REDEEMED, PROCESSED),
          new StatusTransition(REDEEMED, FAILED),
          new StatusTransition(FAILED, REDEEMED));

  private final RedemptionRequestRepository repository;

  @Transactional
  public void changeStatus(UUID id, Status newStatus) {
    transition(findForUpdate(id), newStatus);
  }

  @Transactional
  public void changeStatus(UUID id, Status expectedCurrentStatus, Status newStatus) {
    RedemptionRequest request = findForUpdate(id);
    if (request.getStatus() != expectedCurrentStatus) {
      throw new IllegalStateException(
          "Redemption is no longer "
              + expectedCurrentStatus
              + ": id="
              + id
              + ", status="
              + request.getStatus());
    }
    transition(request, newStatus);
  }

  private void transition(RedemptionRequest request, Status newStatus) {
    Status currentStatus = request.getStatus();
    if (!ALLOWED_TRANSITIONS.contains(new StatusTransition(currentStatus, newStatus))) {
      throw new IllegalStateException(
          "Redemption status transition not allowed: currentStatus="
              + currentStatus
              + ", newStatus="
              + newStatus);
    }

    log.info(
        "RedemptionRequest status change: id={}, currentStatus={}, newStatus={}",
        request.getId(),
        currentStatus,
        newStatus);

    request.setStatus(newStatus);
    if (newStatus == CANCELLED) {
      request.setCancelledAt(clock().instant());
    }
    repository.save(request);
  }

  private RedemptionRequest findForUpdate(UUID id) {
    return repository
        .findByIdForUpdate(id)
        .orElseThrow(() -> new IllegalArgumentException("Redemption request not found: id=" + id));
  }

  private record StatusTransition(Status from, Status to) {}
}
