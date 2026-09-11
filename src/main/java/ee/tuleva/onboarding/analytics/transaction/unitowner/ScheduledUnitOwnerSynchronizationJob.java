package ee.tuleva.onboarding.analytics.transaction.unitowner;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledUnitOwnerSynchronizationJob {

  private static final int SNAPSHOT_RETENTION_DAYS = 35;
  private static final LocalDate PRUNING_FLOOR = LocalDate.of(2026, 8, 26);

  private final UnitOwnerSynchronizer unitOwnerSynchronizer;
  private final UnitOwnerRepository unitOwnerRepository;

  @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledUnitOwnerSynchronizationJob_runDailySync",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void runDailySync() {
    LocalDate snapshotDate = LocalDate.now(ClockHolder.clock());
    log.info("Starting unit owner snapshot synchronization: snapshotDate={}", snapshotDate);
    try {
      unitOwnerSynchronizer.sync(snapshotDate);
      log.info("Unit owner snapshot synchronization completed: snapshotDate={}", snapshotDate);
    } catch (Exception e) {
      log.error(
          "Unit owner snapshot synchronization failed, the first payment emails will not reach"
              + " new unit owners until the next successful sync: snapshotDate={}, error={}",
          snapshotDate,
          e.getMessage(),
          e);
      return;
    }
    pruneOldSnapshots(snapshotDate);
  }

  private void pruneOldSnapshots(LocalDate today) {
    LocalDate cutoff = today.minusDays(SNAPSHOT_RETENTION_DAYS);
    List<LocalDate> prunable =
        unitOwnerRepository.findDistinctSnapshotDates().stream()
            .filter(date -> date.isAfter(PRUNING_FLOOR))
            .filter(date -> date.isBefore(cutoff))
            .filter(date -> date.getDayOfMonth() != 1)
            .filter(date -> date.getDayOfWeek() != DayOfWeek.MONDAY)
            .toList();
    if (prunable.isEmpty()) {
      return;
    }
    int deleted = unitOwnerRepository.deleteBySnapshotDateIn(prunable);
    log.info("Pruned old unit owner snapshots: snapshotDates={}, rows={}", prunable, deleted);
  }
}
