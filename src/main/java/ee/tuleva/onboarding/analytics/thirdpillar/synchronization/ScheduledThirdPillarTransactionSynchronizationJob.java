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

  @Scheduled(cron = "0 30 8 11 3 ?", zone = "Europe/Tallinn")
  public void runFebruaryTransactionsSync() {
    log.info("Starting February transactions synchronization job");
    LocalDate startDate = LocalDate.of(2025, 2, 1);
    LocalDate endDate = LocalDate.of(2025, 2, 28);
    thirdPillarTransactionSynchronizer.syncTransactions(startDate, endDate);
    log.info("February transactions synchronization job completed");
  }
}
