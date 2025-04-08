package ee.tuleva.onboarding.analytics.thirdpillar.synchronization;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.util.Optional;
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
  private final AnalyticsThirdPillarTransactionRepository transactionRepository;

  @Scheduled(cron = "0 0 2 * * ?", zone = "Europe/Tallinn")
  public void run() {
    log.info("Starting transactions synchronization job");
    LocalDate endDate = LocalDate.now(ClockHolder.clock());

    Optional<LocalDate> latestReportingDateOpt = transactionRepository.findLatestReportingDate();
    LocalDate startDate =
        latestReportingDateOpt.orElseGet(
            () -> {
              log.warn(
                  "No existing reporting date found for third pillar transactions. Falling back to synchronizing the last 2 days.");
              return endDate.minusDays(2);
            });

    if (endDate.isBefore(startDate)) {
      log.warn(
          "Calculated endDate {} is before startDate {}. Skipping synchronization.",
          endDate,
          startDate);
      return;
    }

    log.info("Synchronizing third pillar transactions from {} to {}", startDate, endDate);
    thirdPillarTransactionSynchronizer.syncTransactions(startDate, endDate);
    log.info("Transactions synchronization job completed");
  }

  @Scheduled(cron = "0 40 8 8 4 ?", zone = "Europe/Tallinn")
  public void runInitialTransactionsSync() {
    log.info("Starting initial transactions synchronization job");
    LocalDate startDate = LocalDate.of(2025, 4, 1);
    LocalDate endDate = LocalDate.of(2025, 4, 8);
    thirdPillarTransactionSynchronizer.syncTransactions(startDate, endDate);
    log.info("Initial transactions synchronization job completed");
  }
}
