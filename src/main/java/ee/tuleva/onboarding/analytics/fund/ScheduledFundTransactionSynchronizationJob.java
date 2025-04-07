package ee.tuleva.onboarding.analytics.fund;

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
public class ScheduledFundTransactionSynchronizationJob {

  private final FundTransactionSynchronizer fundTransactionSynchronizer;
  private final FundTransactionRepository transactionRepository;

  private static final int DEFAULT_LOOKBACK_DAYS = 2;
  private String fundIsin = "EE3600001707";

  @Scheduled(cron = "0 10 3 * * ?", zone = "Europe/Tallinn")
  public void runDailySync() {
    log.info("Starting scheduled fund transaction synchronization job.");
    LocalDate endDate = LocalDate.now(ClockHolder.clock());
    Optional<LocalDate> latestTransactionDateOpt =
        transactionRepository.findLatestTransactionDate();

    LocalDate startDate =
        latestTransactionDateOpt.orElseGet(
            () -> {
              log.warn(
                  "No existing fund transaction date found. Falling back to synchronizing the last {} days.",
                  DEFAULT_LOOKBACK_DAYS);
              return endDate.minusDays(DEFAULT_LOOKBACK_DAYS);
            });

    if (endDate.isBefore(startDate)) {
      log.warn(
          "Calculated endDate {} is before startDate {}. This might happen if the job runs just after midnight before the first transaction of the day. Skipping synchronization for this run.",
          endDate,
          startDate);
      return;
    }

    log.info("Synchronizing fund transactions from {} to {}", startDate, endDate);
    try {
      fundTransactionSynchronizer.syncTransactions(fundIsin, startDate, endDate);
      log.info("Scheduled fund transaction synchronization job completed successfully.");
    } catch (Exception e) {
      log.error(
          "Scheduled fund transaction synchronization job failed during execution: {}",
          e.getMessage(),
          e);
    }
  }

  @Scheduled(cron = "0 15 10 7 4 ?", zone = "Europe/Tallinn")
  public void runInitialTransactionsSync() {
    log.info("Starting initial fund transaction synchronization job");
    LocalDate startDate = LocalDate.of(2025, 2, 1);
    LocalDate endDate = LocalDate.now(ClockHolder.clock());
    fundTransactionSynchronizer.syncTransactions(fundIsin, startDate, endDate);
    log.info("February fund transaction synchronization job completed");
  }
}
