package ee.tuleva.onboarding.analytics.transaction.unitowner;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.time.Month;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledUnitOwnerSynchronizationJob {

  private final UnitOwnerSynchronizer unitOwnerSynchronizer;

  @Scheduled(cron = "0 30 4 ? * MON", zone = "Europe/Tallinn")
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

  @Scheduled(cron = "0 05 10 24 4 ?", zone = "Europe/Tallinn")
  public void runInitialUnitOwnerSync() {
    LocalDate snapshotDate = LocalDate.now(ClockHolder.clock());
    log.info(
        "Starting initial scheduled unit owner snapshot synchronization job for date {}.",
        snapshotDate);
    try {
      unitOwnerSynchronizer.sync(snapshotDate);
      log.info(
          "Initial scheduled unit owner snapshot synchronization job completed successfully for date {}.",
          snapshotDate);
    } catch (Exception e) {
      log.error(
          "Initial scheduled unit owner snapshot synchronization job failed during execution for date {}: {}",
          snapshotDate,
          e.getMessage(),
          e);
    }
  }

  @Scheduled(cron = "0 16 13 6 5 ?", zone = "Europe/Tallinn")
  public void runHistoricalUnitOwnerSync() {
    LocalDate overallStartDate = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate overallEndDate = LocalDate.of(2025, Month.APRIL, 1);
    log.info(
        "Starting historical unit owner sync for start of month from {} to {}.",
        overallStartDate,
        overallEndDate);

    LocalDate syncDate = overallStartDate.withDayOfMonth(1);

    while (!syncDate.isAfter(overallEndDate)) {
      try {
        log.debug("Synchronizing unit owners for date {}.", syncDate);
        unitOwnerSynchronizer.sync(syncDate);
        log.debug("Successfully synchronized unit owners for date {}.", syncDate);
      } catch (Exception e) {
        log.error(
            "Historical unit owner synchronization failed during execution for date {}: {}",
            syncDate,
            e.getMessage(),
            e);
      }
      syncDate = syncDate.plusMonths(1);
    }
    log.info(
        "Finished historical unit owner sync from {} to {}.", overallStartDate, overallEndDate);
  }
}
