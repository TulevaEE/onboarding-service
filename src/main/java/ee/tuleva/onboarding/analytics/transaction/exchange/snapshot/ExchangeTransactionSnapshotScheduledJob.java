package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExchangeTransactionSnapshotScheduledJob {

  private final ExchangeTransactionSnapshotService exchangeTransactionSnapshotService;

  @Scheduled(cron = "0 45 3 * * SUN", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ExchangeTransactionSnapshotScheduledJob_takeWeeklySnapshot",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  @Transactional
  public void takeWeeklySnapshot() {
    log.info("Starting weekly exchange transaction snapshot job.");
    exchangeTransactionSnapshotService.takeSnapshot("WEEKLY");
  }

  @Scheduled(cron = "0 5 3 1 * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ExchangeTransactionSnapshotScheduledJob_takeMonthlySnapshot",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  @Transactional
  public void takeMonthlySnapshot() {
    log.info("Starting monthly exchange transaction snapshot job.");
    exchangeTransactionSnapshotService.takeSnapshot("MONTHLY");
  }
}
