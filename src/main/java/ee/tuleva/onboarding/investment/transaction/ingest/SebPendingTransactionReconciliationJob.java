package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.pipeline.PipelineStep.EXECUTION_MATCHING;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationRequested;
import ee.tuleva.onboarding.investment.event.trigger.JobTriggerQueue;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.pipeline.PipelineNotifier;
import ee.tuleva.onboarding.pipeline.PipelineRun;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class SebPendingTransactionReconciliationJob {

  private static final int LOOKBACK_DAYS = 7;

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final InvestmentReportService reportService;
  private final SebPendingTransactionReconciliationService reconciliationService;
  private final PipelineTracker pipelineTracker;
  private final PipelineNotifier pipelineNotifier;
  private final SebReconciliationLock reconciliationLock;
  private final JobTriggerQueue jobTriggerQueue;

  @Scheduled(cron = "0 0 9 * * MON-FRI", zone = TIMEZONE)
  @SchedulerLock(
      name = "SebPendingTransactionReconciliationJob.daily",
      lockAtMostFor = "10m",
      lockAtLeastFor = "5m")
  void runOnSchedule() {
    if (!runsUnderTheLock()) {
      log.info(
          "Scheduled SEB pending transactions reconciliation deferred, its lock is held or was"
              + " lost: lock={},"
              + " trigger={}",
          SebReconciliationLock.NAME,
          RunSebPendingTransactionReconciliationRequested.JOB_NAME);
      jobTriggerQueue.defer(RunSebPendingTransactionReconciliationRequested.JOB_NAME);
    }
  }

  private boolean runsUnderTheLock() {
    try {
      return reconciliationLock.runIfFree(this::run);
    } catch (SebReconciliationLockLostException e) {
      return false;
    }
  }

  private void run(BooleanSupplier stillHoldsTheLock) {
    LocalDate today = LocalDate.now(clock);
    if (!publicHolidays.isWorkingDay(today)) {
      log.info(
          "Skipping SEB pending transaction reconciliation on non-working day: date={}", today);
      return;
    }
    log.info(
        "Running scheduled SEB pending transactions reconciliation: today={}, lookbackDays={}",
        today,
        LOOKBACK_DAYS);
    pipelineTracker.start(PipelineRun.PipelineType.IMPORT, EXECUTION_MATCHING);
    try {
      pipelineTracker.stepStarted(EXECUTION_MATCHING);
      Optional<String> firstError = reconcileLookback(today, stillHoldsTheLock);
      if (firstError.isPresent()) {
        pipelineTracker.stepFailed(EXECUTION_MATCHING, firstError.get());
      } else {
        pipelineTracker.stepCompleted(EXECUTION_MATCHING);
      }
    } catch (SebReconciliationLockLostException e) {
      log.error("SEB pending transactions reconciliation stopped, its lock is lost", e);
      pipelineTracker.stepFailed(EXECUTION_MATCHING, e.getMessage());
      throw e;
    } finally {
      PipelineRun pipelineRun = pipelineTracker.current();
      if (pipelineRun != null) {
        pipelineNotifier.sendCompleted(pipelineRun);
      }
      pipelineTracker.clear();
    }
  }

  private Optional<String> reconcileLookback(LocalDate today, BooleanSupplier stillHoldsTheLock) {
    String firstError = null;
    for (int i = 0; i <= LOOKBACK_DAYS; i++) {
      LocalDate date = today.minusDays(i);
      if (!stillHoldsTheLock.getAsBoolean()) {
        throw new SebReconciliationLockLostException(date);
      }
      try {
        Optional<InvestmentReport> report =
            reportService.getReport(SEB, PENDING_TRANSACTIONS, date);
        if (report.isEmpty()) {
          continue;
        }
        reconciliationService.reconcile(report.get(), stillHoldsTheLock);
      } catch (SebReconciliationLockLostException e) {
        throw e;
      } catch (Exception e) {
        log.error(
            "SEB pending transactions reconciliation failed, continuing: reportDate={}", date, e);
        if (firstError == null) {
          firstError = e.getMessage();
        }
      }
    }
    return Optional.ofNullable(firstError);
  }

  @EventListener(RunSebPendingTransactionReconciliationRequested.class)
  void onSebPendingTransactionReconciliationRequested() {
    if (!reconciliationLock.runIfFree(this::run)) {
      log.warn(
          "SEB pending transactions reconciliation skipped, its lock is held: lock={}",
          SebReconciliationLock.NAME);
      throw new SebReconciliationAlreadyRunningException(SebReconciliationLock.NAME);
    }
  }
}
