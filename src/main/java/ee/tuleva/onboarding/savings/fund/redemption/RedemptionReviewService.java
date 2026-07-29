package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionReviewService {

  private final RedemptionRequestRepository repository;
  private final RedemptionStatusService redemptionStatusService;

  @Transactional
  public void approve(UUID id, String approvedBy, String reason) {
    RedemptionRequest request =
        repository
            .findByIdForUpdate(id)
            .orElseThrow(
                () -> new IllegalArgumentException("Redemption request not found: id=" + id));

    if (request.getStatus() != IN_REVIEW) {
      throw new IllegalStateException(
          "Only redemptions in review can be approved: id="
              + id
              + ", status="
              + request.getStatus());
    }

    request.setReviewedBy(approvedBy);
    request.setReviewReason(reason);
    request.setReviewedAt(clock().instant());
    repository.save(request);

    redemptionStatusService.changeStatus(id, VERIFIED);

    log.info("Redemption review approved: id={}, approvedBy={}", id, approvedBy);
  }
}
