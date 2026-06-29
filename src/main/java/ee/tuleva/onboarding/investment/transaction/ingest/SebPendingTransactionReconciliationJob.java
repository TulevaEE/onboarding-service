package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.event.PipelineStep.EXECUTION_MATCHING;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.event.PipelineNotifier;
import ee.tuleva.onboarding.investment.event.PipelineRun;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationRequested;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
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

  @Scheduled(cron = "0 0 9 * * MON-FRI", zone = TIMEZONE)
  @SchedulerLock(
      name = "SebPendingTransactionReconciliationJob",
      lockAtMostFor = "30m",
      lockAtLeastFor = "1m")
  public void run() {
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
      Optional<String> firstError = reconcileLookback(today);
      if (firstError.isPresent()) {
        pipelineTracker.stepFailed(EXECUTION_MATCHING, firstError.get());
      } else {
        pipelineTracker.stepCompleted(EXECUTION_MATCHING);
      }
    } finally {
      PipelineRun pipelineRun = pipelineTracker.current();
      if (pipelineRun != null) {
        pipelineNotifier.sendCompleted(pipelineRun);
      }
      pipelineTracker.clear();
    }
  }

  private Optional<String> reconcileLookback(LocalDate today) {
    String firstError = null;
    for (int i = 0; i <= LOOKBACK_DAYS; i++) {
      LocalDate date = today.minusDays(i);
      try {
        Optional<InvestmentReport> report =
            reportService.getReport(SEB, PENDING_TRANSACTIONS, date);
        if (report.isEmpty()) {
          continue;
        }
        reconciliationService.reconcile(report.get());
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

  @EventListener
  void onSebPendingTransactionReconciliationRequested(
      RunSebPendingTransactionReconciliationRequested event) {
    run();
  }
}
