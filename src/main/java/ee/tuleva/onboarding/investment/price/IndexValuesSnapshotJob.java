package ee.tuleva.onboarding.investment.price;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class IndexValuesSnapshotJob {

  private final IndexValuesSnapshotService snapshotService;

  @Scheduled(cron = "0 30 11 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "IndexValuesSnapshotJob_1130", lockAtMostFor = "55m", lockAtLeastFor = "1m")
  public void createSnapshot1130() {
    createSnapshot();
  }

  @Scheduled(cron = "0 30 15 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "IndexValuesSnapshotJob_1530", lockAtMostFor = "55m", lockAtLeastFor = "1m")
  public void createSnapshot1530() {
    createSnapshot();
  }

  void createSnapshot() {
    try {
      snapshotService.createSnapshot();
    } catch (Exception e) {
      log.error("Index values snapshot creation failed", e);
    }
  }
}
