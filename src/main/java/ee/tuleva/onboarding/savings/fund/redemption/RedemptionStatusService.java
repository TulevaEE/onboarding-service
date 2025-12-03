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
          new StatusTransition(RESERVED, IN_REVIEW),
          new StatusTransition(RESERVED, VERIFIED),
          new StatusTransition(RESERVED, CANCELLED),
          new StatusTransition(RESERVED, FAILED),
          new StatusTransition(IN_REVIEW, VERIFIED),
          new StatusTransition(IN_REVIEW, CANCELLED),
          new StatusTransition(IN_REVIEW, FAILED),
          new StatusTransition(VERIFIED, REDEEMED),
          new StatusTransition(VERIFIED, FAILED),
          new StatusTransition(REDEEMED, PROCESSED),
          new StatusTransition(REDEEMED, FAILED));

  private final RedemptionRequestRepository repository;

  @Transactional
  public void changeStatus(UUID id, Status newStatus) {
    RedemptionRequest request =
        repository
            .findByIdForUpdate(id)
            .orElseThrow(
                () -> new IllegalArgumentException("Redemption request not found: id=" + id));

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
        id,
        currentStatus,
        newStatus);

    request.setStatus(newStatus);
    repository.save(request);
  }

  @Transactional
  public void cancel(UUID id) {
    RedemptionRequest request =
        repository
            .findByIdForUpdate(id)
            .orElseThrow(
                () -> new IllegalArgumentException("Redemption request not found: id=" + id));

    if (request.getStatus() != RESERVED && request.getStatus() != IN_REVIEW) {
      throw new IllegalStateException("Cancellation not allowed: status=" + request.getStatus());
    }

    request.setStatus(CANCELLED);
    request.setCancelledAt(clock().instant());
    repository.save(request);
  }

  private record StatusTransition(Status from, Status to) {}
}
