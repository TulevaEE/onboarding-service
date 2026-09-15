package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!staging")
public class RedemptionVerificationJob {

  static final Duration UNSCREENED_WARNING_AGE = Duration.ofHours(1);

  private final Clock clock;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionVerificationService redemptionVerificationService;
  private final RedemptionHoldService redemptionHoldService;
  private final RedemptionHoldNotifier holdNotifier;

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "RedemptionVerificationJob_runJob",
      lockAtMostFor = "50s",
      lockAtLeastFor = "10s")
  public void runJob() {
    redemptionRequestRepository.findByStatus(RESERVED).stream()
        .forEach(
            request -> {
              try {
                redemptionVerificationService.process(request);
              } catch (Exception e) {
                log.error("Verification failed for redemption request: id={}", request.getId(), e);
              }
            });
  }

  @Scheduled(cron = "0 0 * * * *")
  @SchedulerLock(
      name = "RedemptionVerificationJob_runHourlyChecks",
      lockAtMostFor = "50m",
      lockAtLeastFor = "1m")
  public void runHourlyChecks() {
    warnAboutUnscreenedRequests();
    redemptionHoldService.resendUnsentHoldNotifications();
  }

  private void warnAboutUnscreenedRequests() {
    Instant threshold = clock.instant().minus(UNSCREENED_WARNING_AGE);
    int unscreened =
        redemptionRequestRepository
            .findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(RESERVED, threshold)
            .size();
    if (unscreened > 0) {
      log.warn("{} redemption requests unscreened since before {}", unscreened, threshold);
      holdNotifier.notifyUnscreened(unscreened);
    }
  }
}
