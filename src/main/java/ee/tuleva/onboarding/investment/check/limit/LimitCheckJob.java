package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.JobRunSchedule.LIMIT_CHECK_DAILY_GAP_FILL;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.pipeline.PipelineStep.LIMIT_CHECK;

import ee.tuleva.onboarding.investment.event.NavEventListenerOrder;
import ee.tuleva.onboarding.investment.event.RunLimitCheckBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunLimitCheckRequested;
import ee.tuleva.onboarding.investment.position.FeeAccrualPositionSyncJob;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import ee.tuleva.onboarding.savings.NavCalculationCompleted;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class LimitCheckJob {

  private static final int BACKFILL_DAYS = 25;
  static final int GAP_LOOKBACK_DAYS = 30;

  private final LimitCheckService limitCheckService;
  private final LimitCheckNotifier limitCheckNotifier;
  private final FeeAccrualPositionSyncJob feeAccrualPositionSyncJob;
  private final PipelineTracker pipelineTracker;

  @EventListener
  @Order(NavEventListenerOrder.LIMIT_CHECK)
  void onNavCalculationCompleted(NavCalculationCompleted event) {
    runLimitChecks(event.funds());
  }

  @EventListener
  void onLimitCheckRequested(RunLimitCheckRequested event) {
    runLimitChecks(List.of(TulevaFund.values()));
  }

  private void runLimitChecks(List<TulevaFund> funds) {
    pipelineTracker.stepStarted(LIMIT_CHECK);
    log.info("Starting limit check: funds={}", funds);

    try {
      var run = limitCheckService.runChecksForFunds(funds);
      limitCheckNotifier.notify(run);
      pipelineTracker.stepCompleted(LIMIT_CHECK);

      log.info("Limit check completed: funds={}, resultCount={}", funds, run.results().size());
    } catch (LimitCheckPartialFailureException e) {
      limitCheckNotifier.notify(e.getPartialRun());
      pipelineTracker.stepFailed(LIMIT_CHECK, e.getMessage());
      log.error("Limit check failed", e);
    } catch (Exception e) {
      pipelineTracker.stepFailed(LIMIT_CHECK, e.getMessage());
      log.error("Limit check failed", e);
    }
  }

  @EventListener
  void onLimitCheckBackfillRequested(RunLimitCheckBackfillRequested event) {
    backfillLimitChecks();
  }

  /**
   * The backstop for the NavCalculationCompleted run. Gap-based rather than a fixed window, so a
   * day whose positions arrived late is filled the next evening instead of waiting for a yearly
   * backfill — and a day with nothing missing costs nothing and says nothing.
   */
  @Scheduled(cron = LIMIT_CHECK_DAILY_GAP_FILL, zone = TIMEZONE)
  @SchedulerLock(name = "LimitCheckDailyGapFill", lockAtMostFor = "30m", lockAtLeastFor = "1m")
  void fillLimitCheckGaps() {
    try {
      var gaps = limitCheckService.gapDates(GAP_LOOKBACK_DAYS);
      if (gaps.isEmpty()) {
        log.info("No limit check gaps to fill");
        return;
      }

      log.info("Filling limit check gaps: gaps={}", gaps);
      int synced = feeAccrualPositionSyncJob.sync(GAP_LOOKBACK_DAYS);
      log.info("Fee accrual positions synced before gap fill: positionsWritten={}", synced);

      limitCheckNotifier.notify(limitCheckService.fillGaps(gaps));
    } catch (Exception e) {
      log.error("Limit check gap fill failed", e);
      limitCheckNotifier.notifyBackfillFailed(e);
    }
  }

  void backfillLimitChecks() {
    log.info("Starting limit check backfill");

    try {
      int synced = feeAccrualPositionSyncJob.sync(BACKFILL_DAYS);
      log.info("Fee accrual positions synced before backfill: positionsWritten={}", synced);

      var results = limitCheckService.backfillChecks(BACKFILL_DAYS);
      log.info("Limit check backfill completed: resultCount={}", results.size());
    } catch (Exception e) {
      log.error("Limit check backfill failed", e);
      limitCheckNotifier.notifyBackfillFailed(e);
    }
  }
}
