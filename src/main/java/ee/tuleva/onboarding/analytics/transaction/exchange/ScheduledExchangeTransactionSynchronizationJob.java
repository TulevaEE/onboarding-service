package ee.tuleva.onboarding.analytics.transaction.exchange;

import ee.tuleva.onboarding.deadline.MandateDeadlines;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
public class ScheduledExchangeTransactionSynchronizationJob {

  private final ExchangeTransactionSynchronizer exchangeTransactionSynchronizer;
  private final MandateDeadlinesService mandateDeadlinesService;

  private static final ZoneId TALLINN_ZONE = ZoneId.of("Europe/Tallinn");

  @Scheduled(cron = "0 0 2 * * ?", zone = "Europe/Tallinn")
  public void run() {
    log.info("Starting exchange transactions synchronization job");
    LocalDate startDate = mandateDeadlinesService.getCurrentPeriodStartDate();

    exchangeTransactionSynchronizer.sync(startDate, Optional.empty(), Optional.empty(), false);

    log.info("Transactions exchange synchronization job completed");
  }

  @Scheduled(cron = "0 45 8 8 4 ?", zone = "Europe/Tallinn")
  public void runInitialTransactionsSync() {
    log.info("Starting initial exchange transactions synchronization job");
    LocalDate startDate = LocalDate.of(2024, 12, 1);

    exchangeTransactionSynchronizer.sync(startDate, Optional.empty(), Optional.empty(), false);

    log.info("Finished initial exchange transactions synchronization job completed");
  }

  @Scheduled(cron = "0 10 13 5 5 ?", zone = "Europe/Tallinn")
  public void synchronizeHistoricalData() {
    LocalDate historicalSyncStartDate = LocalDate.of(2017, 3, 28);
    LocalDate historicalSyncEndDate = LocalDate.of(2024, 11, 30);

    log.info(
        "Starting historical exchange transactions synchronization job from {} to {}",
        historicalSyncStartDate,
        historicalSyncEndDate);

    Instant firstInstant = historicalSyncStartDate.atStartOfDay(TALLINN_ZONE).toInstant();
    MandateDeadlines firstDeadlines = mandateDeadlinesService.getDeadlines(firstInstant);
    LocalDate currentPeriodStartDate = firstDeadlines.getCurrentPeriodStartDate();

    int periodsSynced = 0;
    while (!currentPeriodStartDate.isAfter(historicalSyncEndDate)) {
      log.info("Synchronizing historical period starting from: {}", currentPeriodStartDate);
      try {
        exchangeTransactionSynchronizer.sync(
            currentPeriodStartDate, Optional.empty(), Optional.empty(), false);
        periodsSynced++;

        Instant currentPeriodInstant =
            currentPeriodStartDate.atStartOfDay(TALLINN_ZONE).toInstant();
        MandateDeadlines currentDeadlines =
            mandateDeadlinesService.getDeadlines(currentPeriodInstant);

        LocalDate currentPeriodEndDate = getPeriodEndDateFromDeadlines(currentDeadlines);

        LocalDate nextPeriodStartDate = currentPeriodEndDate.plusDays(1);

        if (nextPeriodStartDate.isBefore(currentPeriodStartDate)
            || nextPeriodStartDate.isEqual(currentPeriodStartDate)) {
          log.error(
              "Failed to advance to the next period. Current start: {}, Calculated end: {}, Calculated next start: {}. Aborting historical sync.",
              currentPeriodStartDate,
              currentPeriodEndDate,
              nextPeriodStartDate);
          break;
        }

        currentPeriodStartDate = nextPeriodStartDate;

      } catch (Exception e) {
        log.error(
            "Error synchronizing historical period starting from {}. Aborting historical sync.",
            currentPeriodStartDate,
            e);
        break;
      }
    }

    log.info(
        "Historical exchange transactions synchronization job completed. Synced {} periods.",
        periodsSynced);
  }

  private LocalDate getPeriodEndDateFromDeadlines(MandateDeadlines deadlines) {
    Instant periodEndInstant = deadlines.getPeriodEnding();
    return LocalDate.ofInstant(periodEndInstant, TALLINN_ZONE);
  }
}
