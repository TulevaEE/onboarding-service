package ee.tuleva.onboarding.investment.event.trigger;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobTriggerQueue {

  private static final String PENDING = "PENDING";
  private static final List<String> WAITING_OR_RUNNING = List.of(PENDING, "PROCESSING");

  private final JobTriggerRepository repository;

  public void defer(String jobName) {
    Optional<JobTrigger> waitingOrRunning =
        repository.findFirstByJobNameAndStatusIn(jobName, WAITING_OR_RUNNING);
    if (waitingOrRunning.isEmpty()) {
      repository.save(JobTrigger.builder().jobName(jobName).build());
      log.info("Job deferred to the next poll: jobName={}", jobName);
      return;
    }
    JobTrigger existing = waitingOrRunning.get();
    if (PENDING.equals(existing.getStatus())) {
      existing.setCreatedAt(clock().instant());
      repository.save(existing);
    }
  }
}
