package ee.tuleva.onboarding.accounting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("production")
@RequiredArgsConstructor
public class GeneralLedgerSyncJob {

  private final GeneralLedgerSync generalLedgerSync;

  @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "GeneralLedgerSyncJob_sync", lockAtMostFor = "40m", lockAtLeastFor = "1m")
  public void sync() {
    generalLedgerSync.entities().forEach(this::sync);
  }

  private void sync(String entity) {
    try {
      generalLedgerSync.sync(entity);
    } catch (RuntimeException e) {
      log.error("General ledger sync failed: entity={}", entity, e);
    }
  }
}
