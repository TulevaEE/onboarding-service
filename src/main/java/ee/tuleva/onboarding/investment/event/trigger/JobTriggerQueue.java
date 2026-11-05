package ee.tuleva.onboarding.investment.event.trigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobTriggerQueue {

  private static final String PENDING = "PENDING";

  private final JobTriggerRepository repository;

  public void enqueueUnlessPending(String jobName) {
    if (repository.existsByJobNameAndStatus(jobName, PENDING)) {
      log.info("Job trigger already waiting, not queueing another: jobName={}", jobName);
      return;
    }
    repository.save(JobTrigger.builder().jobName(jobName).build());
    log.info("Job trigger queued for the next poll: jobName={}", jobName);
  }
}
