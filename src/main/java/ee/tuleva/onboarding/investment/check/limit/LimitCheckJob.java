package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.JobRunSchedule.LIMIT_CHECK_BACKFILL;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.event.FeeAccrualPositionsSynced;
import ee.tuleva.onboarding.investment.event.RunLimitCheckRequested;
import ee.tuleva.onboarding.investment.position.FeeAccrualPositionSyncJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class LimitCheckJob {

  private final LimitCheckService limitCheckService;
  private final LimitCheckNotifier limitCheckNotifier;
  private final FeeAccrualPositionSyncJob feeAccrualPositionSyncJob;

  @EventListener
  void onFeeAccrualPositionsSynced(FeeAccrualPositionsSynced event) {
    runLimitChecks();
  }

  @EventListener
  void onLimitCheckRequested(RunLimitCheckRequested event) {
    runLimitChecks();
  }

  private void runLimitChecks() {
    log.info("Starting limit check");

    try {
      var results = limitCheckService.runChecks();
      limitCheckNotifier.notify(results);

      log.info("Limit check completed: resultCount={}", results.size());
    } catch (Exception e) {
      log.error("Limit check failed", e);
    }
  }

  @Scheduled(cron = LIMIT_CHECK_BACKFILL, zone = TIMEZONE)
  @SchedulerLock(name = "LimitCheckBackfillJob", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  void backfillLimitChecks() {
    log.info("Starting limit check backfill");

    try {
      int synced = feeAccrualPositionSyncJob.sync(10);
      log.info("Fee accrual positions synced before backfill: positionsWritten={}", synced);

      var results = limitCheckService.backfillChecks(10);
      log.info("Limit check backfill completed: resultCount={}", results.size());
    } catch (Exception e) {
      log.error("Limit check backfill failed", e);
    }
  }
}
