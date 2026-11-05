package ee.tuleva.onboarding.investment.event.trigger;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobTriggerQueue {

  private static final String PENDING = "PENDING";

  private final JobTriggerRepository repository;

  public void defer(String jobName) {
    Optional<JobTrigger> waiting = repository.findFirstByJobNameAndStatus(jobName, PENDING);
    if (waiting.isPresent()) {
      waiting.get().setCreatedAt(clock().instant());
      repository.save(waiting.get());
      return;
    }
    repository.save(JobTrigger.builder().jobName(jobName).build());
    log.info("Job deferred to the next poll: jobName={}", jobName);
  }
}
