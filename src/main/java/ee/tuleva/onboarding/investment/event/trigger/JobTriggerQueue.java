package ee.tuleva.onboarding.investment.event.trigger;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobTriggerQueue {

  private final JobTriggerRepository repository;

  public void defer(String jobName) {
    if (repository.restartPendingBudget(jobName, clock().instant()) > 0) {
      return;
    }
    repository.save(JobTrigger.builder().jobName(jobName).build());
    log.info("Job deferred to the next poll: jobName={}", jobName);
  }
}
