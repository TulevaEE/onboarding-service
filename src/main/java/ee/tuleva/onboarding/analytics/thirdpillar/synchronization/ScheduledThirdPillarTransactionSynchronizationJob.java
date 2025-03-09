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

  @Scheduled(cron = "0 25 6 9 3 ? 2025", zone = "Europe/Tallinn")
  public void run() {
    log.info("Starting transactions synchronization job");
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = endDate.minusDays(10);
    thirdPillarTransactionSynchronizer.syncTransactions(startDate, endDate);
    log.info("Transactions synchronization job completed");
  }
}
