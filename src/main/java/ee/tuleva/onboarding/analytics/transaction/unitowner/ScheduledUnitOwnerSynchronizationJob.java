package ee.tuleva.onboarding.analytics.transaction.unitowner;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @Scheduled(cron = "0 07 10 21 10 ?", zone = "Europe/Tallinn"),
    @Scheduled(cron = "0 30 4 ? * MON", zone = "Europe/Tallinn")
  })
  public void runDailySync() {
    LocalDate snapshotDate = LocalDate.now(ClockHolder.clock());
    log.info(
        "Starting scheduled unit owner snapshot synchronization job for date {}.", snapshotDate);
    try {
      unitOwnerSynchronizer.sync(snapshotDate);
      log.info(
          "Scheduled unit owner snapshot synchronization job completed successfully for date {}.",
          snapshotDate);
    } catch (Exception e) {
      log.error(
          "Scheduled unit owner snapshot synchronization job failed during execution for date {}: {}",
          snapshotDate,
          e.getMessage(),
          e);
    }
  }

  @Scheduled(cron = "0 0 5 1 * ?", zone = "Europe/Tallinn")
  public void runMonthlySync() {
    LocalDate snapshotDate = LocalDate.now(ClockHolder.clock());
    log.info(
        "Starting monthly scheduled unit owner snapshot synchronization job for date {}.",
        snapshotDate);
    try {
      unitOwnerSynchronizer.sync(snapshotDate);
      log.info(
          "Monthly scheduled unit owner snapshot synchronization job completed successfully for date {}.",
          snapshotDate);
    } catch (Exception e) {
      log.error(
          "Monthly scheduled unit owner snapshot synchronization job failed during execution for date {}: {}",
          snapshotDate,
          e.getMessage(),
          e);
    }
  }
}
