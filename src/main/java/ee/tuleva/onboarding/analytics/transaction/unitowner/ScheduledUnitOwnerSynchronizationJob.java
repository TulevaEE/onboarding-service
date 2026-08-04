package ee.tuleva.onboarding.analytics.transaction.unitowner;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledUnitOwnerSynchronizationJob {

  private final UnitOwnerSynchronizer unitOwnerSynchronizer;

  @Schedules({
    @Scheduled(cron = "0 20 10 1 12 ?", zone = "Europe/Tallinn"),
    @Scheduled(cron = "0 30 4 ? * MON", zone = "Europe/Tallinn")
  })
  @SchedulerLock(
      name = "ScheduledUnitOwnerSynchronizationJob_runDailySync",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void runDailySync() {
    synchronizeSnapshot();
  }

  @Scheduled(cron = "0 0 5 1 * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledUnitOwnerSynchronizationJob_runMonthlySync",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void runMonthlySync() {
    synchronizeSnapshot();
  }

  private void synchronizeSnapshot() {
    LocalDate snapshotDate = LocalDate.now(ClockHolder.clock());
    log.info("Starting unit owner snapshot synchronization: snapshotDate={}", snapshotDate);
    try {
      unitOwnerSynchronizer.sync(snapshotDate);
      log.info("Unit owner snapshot synchronization completed: snapshotDate={}", snapshotDate);
    } catch (Exception e) {
      log.error(
          "Unit owner snapshot synchronization failed, analytics monthly series will have no data"
              + " for this month and a first-of-month snapshot cannot be recreated later:"
              + " snapshotDate={}, error={}",
          snapshotDate,
          e.getMessage(),
          e);
    }
  }
}
