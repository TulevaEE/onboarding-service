package ee.tuleva.onboarding.investment.event.trigger;

import static ee.tuleva.onboarding.investment.JobRunSchedule.JOB_TRIGGER_POLL;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import ee.tuleva.onboarding.investment.event.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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

  static final Map<String, Supplier<Object>> EVENTS =
      Map.ofEntries(
          Map.entry("TrackingDifferenceJob", RunTrackingDifferenceCheckRequested::new),
          Map.entry(
              "TrackingDifferenceBackfillJob", () -> new RunTrackingDifferenceBackfillRequested(7)),
          Map.entry("LimitCheckJob", RunLimitCheckRequested::new),
          Map.entry("FeeCheckJob", RunFeeCheckRequested::new),
          Map.entry("FeeSettlementCheckJob", RunFeeSettlementCheckRequested::new),
          Map.entry("LimitCheckBackfillJob", RunLimitCheckBackfillRequested::new),
          Map.entry("ReportImportJob", RunReportImportRequested::new),
          Map.entry("FundPositionImportJob", RunFundPositionImportRequested::new),
          Map.entry("FeeAccrualPositionSyncJob", RunFeeAccrualPositionSyncRequested::new),
          Map.entry("TransactionCommandJob", RunTransactionCommandRequested::new),
          Map.entry(
              RunSebPendingTransactionReconciliationRequested.JOB_NAME,
              RunSebPendingTransactionReconciliationRequested::new),
          Map.entry(
              RunSebPendingTransactionReconciliationBackfillRequested.JOB_NAME,
              RunSebPendingTransactionReconciliationBackfillRequested::new),
          Map.entry("PortfolioCostBasisJob", RunPortfolioCostBasisRequested::new),
          Map.entry("PortfolioCostBasisSelfHealJob", RunPortfolioCostBasisSelfHealRequested::new),
          Map.entry("PortfolioReconciliationJob", RunPortfolioReconciliationRequested::new),
          Map.entry("SettlementCheckJob", RunOverdueSettlementRequested::new),
          // Alias kept so existing manual triggers keep working after the rename.
          Map.entry("OverdueSettlementJob", RunOverdueSettlementRequested::new),
          Map.entry("PevaRavaPhaseUpdateJob", RunPevaRavaPhaseUpdateRequested::new),
          Map.entry("PevaRavaFlowRecalcJob", RunPevaRavaFlowRecalcRequested::new),
          Map.entry("R16FlowRecalcJob", RunR16FlowRecalcRequested::new),
          Map.entry("TdAttributionJob", RunTdAttributionMonthlyRequested::new),
          Map.entry("TdAttributionBackfillJob", () -> new RunTdAttributionBackfillRequested(6)),
          Map.entry("OcfCalculationJob", RunOcfCalculationRequested::new),
          Map.entry("OwnershipLimitCheckJob", RunOwnershipLimitCheckRequested::new),
          Map.entry("InstrumentFeeRateJob", RunInstrumentFeeRateResolveRequested::new),
          Map.entry("RiskIndicatorJob", () -> new RunRiskIndicatorRequested(28)),
          Map.entry("RiskIndicatorBackfillJob", () -> new RunRiskIndicatorRequested(120)));

  static final Map<String, Function<LocalDate, Object>> RESUMABLE_FROM_A_DATE =
      Map.of(
          RunSebPendingTransactionReconciliationBackfillRequested.JOB_NAME,
          RunSebPendingTransactionReconciliationBackfillRequested::new);

  private static final Duration STAYS_PENDING_WHILE_ITS_JOB_IS_BUSY_FOR_AT_MOST =
      Duration.ofHours(6);

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
      var eventFactory = eventFor(trigger.getJobName());
      if (eventFactory.isEmpty()) {
        markFailed(trigger, "Unknown job: " + trigger.getJobName());
        continue;
      }

      markProcessing(trigger);
      try {
        eventPublisher.publishEvent(eventFactory.get().get());
        markCompleted(trigger);
        log.info("Job trigger completed: jobName={}", trigger.getJobName());
      } catch (JobBusyException e) {
        retryOnTheNextPollUnlessBusyTooLong(trigger, e);
      } catch (Exception e) {
        markFailed(trigger, e.getMessage() == null ? e.toString() : e.getMessage());
        log.error("Job trigger failed: jobName={}", trigger.getJobName(), e);
      }
    }
  }

  private static Optional<Supplier<Object>> eventFor(String jobName) {
    Supplier<Object> event = EVENTS.get(jobName);
    if (event != null) {
      return Optional.of(event);
    }
    String[] jobAndDate = jobName.split(":", 2);
    if (jobAndDate.length < 2 || !RESUMABLE_FROM_A_DATE.containsKey(jobAndDate[0])) {
      return Optional.empty();
    }
    try {
      LocalDate from = LocalDate.parse(jobAndDate[1]);
      Function<LocalDate, Object> resumed = RESUMABLE_FROM_A_DATE.get(jobAndDate[0]);
      return Optional.of(() -> resumed.apply(from));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private void retryOnTheNextPollUnlessBusyTooLong(JobTrigger trigger, JobBusyException busy) {
    var createdAt = trigger.getCreatedAt();
    if (createdAt != null
        && !createdAt
            .plus(STAYS_PENDING_WHILE_ITS_JOB_IS_BUSY_FOR_AT_MOST)
            .isAfter(clock().instant())) {
      markFailed(trigger, "Still busy after 6 hours: " + busy.getMessage());
      log.error("Job trigger failed, its job stayed busy: jobName={}", trigger.getJobName(), busy);
      return;
    }
    trigger.setStatus("PENDING");
    repository.save(trigger);
    log.info(
        "Job trigger left pending, its job is busy: jobName={}, reason={}",
        trigger.getJobName(),
        busy.getMessage());
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
