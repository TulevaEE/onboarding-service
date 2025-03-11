package ee.tuleva.onboarding.analytics.thirdpillar.synchronization;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledThirdPillarTransactionSynchronizationJob {

  private final ThirdPillarTransactionSynchronizer thirdPillarTransactionSynchronizer;

  @Scheduled(cron = "0 0 2 * * ?", zone = "Europe/Tallinn")
  public void run() {
    log.info("Starting transactions synchronization job");
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = endDate.minusDays(2);
    thirdPillarTransactionSynchronizer.syncTransactions(startDate, endDate);
    log.info("Transactions synchronization job completed");
  }

  @Scheduled(cron = "0 30 8 11 3 ?", zone = "Europe/Tallinn")
  public void runFebruaryTransactionsSync() {
    log.info("Starting February transactions synchronization job");
    LocalDate startDate = LocalDate.of(2025, 2, 1);
    LocalDate endDate = LocalDate.of(2025, 2, 28);
    thirdPillarTransactionSynchronizer.syncTransactions(startDate, endDate);
    log.info("February transactions synchronization job completed");
  }
}
