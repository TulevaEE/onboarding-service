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
      Map.ofEntries(
          Map.entry("TrackingDifferenceJob", RunTrackingDifferenceCheckRequested::new),
          Map.entry("TrackingDifferenceBackfillJob", RunTrackingDifferenceBackfillRequested::new),
          Map.entry("LimitCheckJob", RunLimitCheckRequested::new),
          Map.entry("LimitCheckBackfillJob", RunLimitCheckBackfillRequested::new),
          Map.entry("ReportImportJob", RunReportImportRequested::new),
          Map.entry("FundPositionImportJob", RunFundPositionImportRequested::new),
          Map.entry("FeeAccrualPositionSyncJob", RunFeeAccrualPositionSyncRequested::new),
          Map.entry("TransactionCommandJob", RunTransactionCommandRequested::new),
          Map.entry(
              "SebPendingTransactionReconciliationJob",
              RunSebPendingTransactionReconciliationRequested::new),
          Map.entry(
              "SebPendingTransactionReconciliationBackfillJob",
              RunSebPendingTransactionReconciliationBackfillRequested::new),
          Map.entry("PortfolioCostBasisJob", RunPortfolioCostBasisRequested::new),
          Map.entry("PortfolioCostBasisSelfHealJob", RunPortfolioCostBasisSelfHealRequested::new),
          Map.entry("PortfolioReconciliationJob", RunPortfolioReconciliationRequested::new),
          Map.entry("OverdueSettlementJob", RunOverdueSettlementRequested::new),
          Map.entry("TdAttributionJob", RunTdAttributionMonthlyRequested::new),
          Map.entry("TdAttributionBackfillJob", () -> new RunTdAttributionBackfillRequested(6)),
          Map.entry("OcfCalculationJob", RunOcfCalculationRequested::new));

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
