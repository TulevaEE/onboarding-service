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
          new StatusTransition(PENDING, RESERVED),
          new StatusTransition(PENDING, CANCELLED),
          new StatusTransition(PENDING, FAILED),
          new StatusTransition(RESERVED, PAID_OUT),
          new StatusTransition(RESERVED, FAILED),
          new StatusTransition(PAID_OUT, COMPLETED),
          new StatusTransition(PAID_OUT, FAILED));

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

    if (request.getStatus() != PENDING) {
      throw new IllegalStateException("Cancellation not allowed: status=" + request.getStatus());
    }

    request.setStatus(CANCELLED);
    request.setCancelledAt(clock().instant());
    repository.save(request);
  }

  private record StatusTransition(Status from, Status to) {}
}
