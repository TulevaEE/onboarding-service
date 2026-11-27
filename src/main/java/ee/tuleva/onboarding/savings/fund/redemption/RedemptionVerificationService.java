package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.holdReasons;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.outcome;

import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Evidence;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionVerificationService {

  private static final Duration SCREENING_RETRY_BACKOFF = Duration.ofMinutes(5);

  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final RedemptionHoldService holdService;
  private final RedemptionEvidenceCollector evidenceCollector;
  private final SavingFundDeadlinesService deadlinesService;
  private final Clock clock;

  @Transactional
  public void process(RedemptionRequest request) {
    if (attemptedWithinBackoff(request)) {
      return;
    }
    log.info(
        "Processing verification for redemption request: id={}, party={}",
        request.getId(),
        request.getPartyId());

    Evidence evidence = evidenceCollector.collect(request);

    switch (outcome(evidence)) {
      case RETRY -> retryLater(request, evidence);
      case FREEZE -> holdService.freeze(request.getId());
      case HOLD_PAYOUT -> holdPayoutAndVerify(request, evidence);
      case CLEAR -> {
        log.info(
            "Redemption verification passed: id={}, party={}, takeoverSignals={}",
            request.getId(),
            request.getPartyId(),
            evidence.takeoverSignals());
        redemptionStatusService.changeStatus(request.getId(), RESERVED, VERIFIED);
      }
    }
  }

  private void holdPayoutAndVerify(RedemptionRequest request, Evidence evidence) {
    holdService.holdPayout(request.getId(), holdReasons(evidence), evidence.takeoverSignals());
    redemptionStatusService.changeStatus(request.getId(), RESERVED, VERIFIED);
  }

  private void retryLater(RedemptionRequest request, Evidence evidence) {
    if (!canStillRetry(request)) {
      holdPayoutAndVerify(request, evidence);
      return;
    }
    redemptionRequestRepository.markVerificationAttempted(request.getId(), Instant.now(clock));
    log.info(
        "Verification input unavailable, retrying until deadline: id={}, partyType={},"
            + " holdReasonsAtDeadline={}, deadline={}",
        request.getId(),
        request.getPartyId().type(),
        holdReasons(evidence),
        deadlinesService.getScreeningRetryDeadline(request));
  }

  private boolean attemptedWithinBackoff(RedemptionRequest request) {
    Instant attemptedAt = request.getVerificationAttemptedAt();
    return attemptedAt != null
        && attemptedAt.isAfter(Instant.now(clock).minus(SCREENING_RETRY_BACKOFF));
  }

  private boolean canStillRetry(RedemptionRequest request) {
    return Instant.now(clock).isBefore(deadlinesService.getScreeningRetryDeadline(request));
  }
}
