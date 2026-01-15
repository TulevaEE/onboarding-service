package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!staging")
public class RedemptionVerificationJob {

  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionVerificationService redemptionVerificationService;

  // @Scheduled(fixedRateString = "1m")
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
}
