package ee.tuleva.onboarding.investment.event.trigger;

import static ee.tuleva.onboarding.investment.JobRunSchedule.JOB_TRIGGER_POLL;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import ee.tuleva.onboarding.investment.event.*;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
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
          Map.entry("SettlementFromBankHistoryJob", RunSettlementFromBankHistoryRequested::new),
          Map.entry(RunSettlementFromBankRequested.JOB_NAME, RunSettlementFromBankRequested::new),
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

  static final Map<String, Function<LocalDate, Object>> TAKING_A_DATE =
      Map.of(
          RunSebPendingTransactionReconciliationBackfillRequested.JOB_NAME,
          RunSebPendingTransactionReconciliationBackfillRequested::new,
          RunSebPendingTransactionReportReconciliationRequested.JOB_NAME,
          RunSebPendingTransactionReportReconciliationRequested::new);

  private static final Duration STAYS_PENDING_WHILE_ITS_JOB_IS_BUSY_FOR_AT_MOST =
      Duration.ofHours(6);

  private static final Duration A_POLL_STARTS_NO_TRIGGER_AFTER_ITS_60_MINUTE_LEASE_LESS =
      Duration.ofMinutes(55);

  private static final String PENDING = "PENDING";
  private static final String PROCESSING = "PROCESSING";

  private final JobTriggerRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final OperationsNotificationService notificationService;
  private final JobTriggerLease jobTriggerLease;

  @Scheduled(cron = JOB_TRIGGER_POLL)
  @SchedulerLock(name = "JobTriggerPoller", lockAtMostFor = "60m", lockAtLeastFor = "30s")
  void poll() {
    var pending = repository.findByStatusOrderByCreatedAtAsc(PENDING);
    if (pending.isEmpty()) {
      return;
    }

    log.debug("Found pending job triggers: count={}", pending.size());
    Instant leaseDeadline =
        clock().instant().plus(A_POLL_STARTS_NO_TRIGGER_AFTER_ITS_60_MINUTE_LEASE_LESS);
    jobTriggerLease.runUntil(leaseDeadline, () -> runPending(pending, leaseDeadline));
  }

  private void runPending(List<JobTrigger> pending, Instant leaseDeadline) {
    for (var listed : pending) {
      if (!clock().instant().isBefore(leaseDeadline)) {
        log.info(
            "Job trigger poll leaves the rest for the next poll, its lease is nearly spent:"
                + " nextJobName={}",
            listed.getJobName());
        return;
      }
      run(listed.getId(), listed.getJobName());
    }
  }

  private void run(Long id, String jobName) {
    var eventFactory = eventFor(jobName);
    if (eventFactory.isEmpty()) {
      markFailed(id, jobName, PENDING, "Unknown job: " + jobName);
      return;
    }
    if (repository.claim(id, clock().instant()) == 0) {
      log.info("Job trigger skipped, another poll already took it: jobName={}", jobName);
      return;
    }
    try {
      eventPublisher.publishEvent(eventFactory.get().get());
      if (moved(repository.complete(id, clock().instant()), jobName, PROCESSING, "COMPLETED")) {
        log.info("Job trigger completed: jobName={}", jobName);
      }
    } catch (JobBusyException e) {
      retryOnTheNextPollUnlessBusyTooLong(id, jobName, e);
    } catch (Exception e) {
      markFailed(id, jobName, PROCESSING, e.getMessage() == null ? e.toString() : e.getMessage());
      log.error("Job trigger failed: jobName={}", jobName, e);
    }
  }

  private static Optional<Supplier<Object>> eventFor(String jobName) {
    Supplier<Object> event = EVENTS.get(jobName);
    if (event != null) {
      return Optional.of(event);
    }
    String[] jobAndDate = jobName.split(":", 2);
    if (jobAndDate.length < 2 || !TAKING_A_DATE.containsKey(jobAndDate[0])) {
      return Optional.empty();
    }
    try {
      LocalDate date = LocalDate.parse(jobAndDate[1]);
      Function<LocalDate, Object> forTheDate = TAKING_A_DATE.get(jobAndDate[0]);
      return Optional.of(() -> forTheDate.apply(date));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private void retryOnTheNextPollUnlessBusyTooLong(Long id, String jobName, JobBusyException busy) {
    Optional<Instant> expiredQueuedAt =
        repository
            .findById(id)
            .map(JobTrigger::getCreatedAt)
            .filter(JobTriggerPoller::busyForTooLongSince);
    if (expiredQueuedAt.isPresent()) {
      if (markFailed(id, jobName, PROCESSING, "Still busy after 6 hours: " + busy.getMessage())) {
        log.error(
            "Job trigger expired, its job stayed busy for 6 hours: jobName={}, queuedAt={},"
                + " reason={}",
            jobName,
            expiredQueuedAt.get(),
            busy.getMessage());
        postExpiry(jobName);
      }
      return;
    }
    if (moved(repository.requeue(id), jobName, PROCESSING, PENDING)) {
      log.debug(
          "Job trigger left pending, its job is busy: jobName={}, reason={}",
          jobName,
          busy.getMessage());
    }
  }

  private static boolean busyForTooLongSince(Instant queuedAt) {
    return !queuedAt
        .plus(STAYS_PENDING_WHILE_ITS_JOB_IS_BUSY_FOR_AT_MOST)
        .isAfter(clock().instant());
  }

  private void postExpiry(String jobName) {
    try {
      notificationService.sendMessage(expiredMessage(jobName), INVESTMENT, ERROR);
    } catch (RuntimeException e) {
      log.error("Job trigger expiry could not be posted to Slack: jobName={}", jobName, e);
    }
  }

  private static String expiredMessage(String jobName) {
    return "Käivitaja "
        + jobName
        + " aegus 6 tunni järel, selle töö lukk oli kogu aeg hõivatud.\n"
        + "Käivita uuesti: INSERT INTO investment_job_trigger (job_name) VALUES ('"
        + jobName
        + "');";
  }

  private boolean markFailed(Long id, String jobName, String expectedStatus, String errorMessage) {
    return moved(
        repository.fail(id, expectedStatus, clock().instant(), errorMessage),
        jobName,
        expectedStatus,
        "FAILED");
  }

  private static boolean moved(
      int updatedRows, String jobName, String expectedStatus, String intendedStatus) {
    if (updatedRows > 0) {
      return true;
    }
    log.warn(
        "Job trigger left as it is, it was no longer in the status the poll expected:"
            + " jobName={}, expectedStatus={}, intendedStatus={}",
        jobName,
        expectedStatus,
        intendedStatus);
    return false;
  }
}
