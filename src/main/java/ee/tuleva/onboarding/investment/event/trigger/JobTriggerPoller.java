package ee.tuleva.onboarding.investment.event.trigger;

import static ee.tuleva.onboarding.investment.JobRunSchedule.JOB_TRIGGER_POLL;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import ee.tuleva.onboarding.investment.event.*;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
class JobTriggerPoller {

  private static final Map<String, Supplier<Object>> EVENTS =
      Map.of(
          "TrackingDifferenceJob", RunTrackingDifferenceCheckRequested::new,
          "TrackingDifferenceBackfillJob", RunTrackingDifferenceBackfillRequested::new,
          "LimitCheckJob", RunLimitCheckRequested::new,
          "LimitCheckBackfillJob", RunLimitCheckBackfillRequested::new,
          "ReportImportJob", RunReportImportRequested::new,
          "FundPositionImportJob", RunFundPositionImportRequested::new,
          "FeeAccrualPositionSyncJob", RunFeeAccrualPositionSyncRequested::new,
          "TransactionCommandJob", RunTransactionCommandRequested::new);

  private final JobTriggerRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(cron = JOB_TRIGGER_POLL)
  @SchedulerLock(name = "JobTriggerPoller", lockAtMostFor = "60m", lockAtLeastFor = "30s")
  void poll() {
    var pending = repository.findByStatusOrderByCreatedAtAsc("PENDING");
    if (pending.isEmpty()) {
      return;
    }

    log.info("Found pending job triggers: count={}", pending.size());

    for (var trigger : pending) {
      var eventFactory = EVENTS.get(trigger.getJobName());
      if (eventFactory == null) {
        markFailed(trigger, "Unknown job: " + trigger.getJobName());
        continue;
      }

      markProcessing(trigger);
      try {
        eventPublisher.publishEvent(eventFactory.get());
        markCompleted(trigger);
        log.info("Job trigger completed: jobName={}", trigger.getJobName());
      } catch (Exception e) {
        markFailed(trigger, e.getMessage());
        log.error("Job trigger failed: jobName={}", trigger.getJobName(), e);
      }
    }
  }

  private void markProcessing(JobTrigger trigger) {
    trigger.setStatus("PROCESSING");
    trigger.setStartedAt(clock().instant());
    repository.save(trigger);
  }

  private void markCompleted(JobTrigger trigger) {
    trigger.setStatus("COMPLETED");
    trigger.setCompletedAt(clock().instant());
    repository.save(trigger);
  }

  private void markFailed(JobTrigger trigger, String errorMessage) {
    trigger.setStatus("FAILED");
    trigger.setCompletedAt(clock().instant());
    trigger.setErrorMessage(errorMessage);
    repository.save(trigger);
  }
}
