package ee.tuleva.onboarding.analytics.transaction.fund;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.util.Optional;
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
public class ScheduledFundTransactionSynchronizationJob {

  private final FundTransactionSynchronizer fundTransactionSynchronizer;
  private final FundTransactionRepository transactionRepository;

  private static final int DEFAULT_LOOKBACK_DAYS = 2;
  private static final int MAX_LOOKBACK_MONTHS = 6;

  private final String thirdPillarIsin = "EE3600001707";
  private final String secondPillarIsin = "EE3600109435";
  private final String secondPillarBondIsin = "EE3600109443";

  @Scheduled(cron = "0 10 3 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledFundTransactionSynchronizationJob_runDailySyncForThirdPillar",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void runDailySyncForThirdPillar() {
    log.info(
        "Starting scheduled fund transaction synchronization job for ISIN {}.", thirdPillarIsin);
    syncTransactionsForIsin(thirdPillarIsin);
  }

  @Scheduled(cron = "0 20 3 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledFundTransactionSynchronizationJob_runDailySyncForSecondPillar",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void runDailySyncForSecondPillar() {
    log.info(
        "Starting scheduled fund transaction synchronization job for ISIN {}.", secondPillarIsin);
    syncTransactionsForIsin(secondPillarIsin);
  }

  @Scheduled(cron = "0 30 3 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledFundTransactionSynchronizationJob_runDailySyncForSecondPillarBond",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void runDailySyncForSecondPillarBond() {
    log.info(
        "Starting scheduled fund transaction synchronization job for ISIN {}.",
        secondPillarBondIsin);
    syncTransactionsForIsin(secondPillarBondIsin);
  }

  private void syncTransactionsForIsin(String isin) {
    LocalDate endDate = LocalDate.now(ClockHolder.clock());
    Optional<LocalDate> latestTransactionDateOpt =
        transactionRepository.findLatestTransactionDateByIsin(isin);

    log.debug(
        "Result from findLatestTransactionDateByIsin({}) : {}", isin, latestTransactionDateOpt);

    LocalDate initialStartDate =
        latestTransactionDateOpt.orElseGet(
            () -> {
              log.warn(
                  "No existing fund transaction date found for ISIN {}. Falling back to synchronizing the last {} days.",
                  isin,
                  DEFAULT_LOOKBACK_DAYS);
              return endDate.minusDays(DEFAULT_LOOKBACK_DAYS);
            });

    LocalDate sixMonthsAgo = endDate.minusMonths(MAX_LOOKBACK_MONTHS);
    LocalDate startDate = initialStartDate;

    if (initialStartDate.isBefore(sixMonthsAgo)) {
      log.info(
          "Initial startDate {} for ISIN {} is more than {} months ago. Capping startDate to {}.",
          initialStartDate,
          isin,
          MAX_LOOKBACK_MONTHS,
          sixMonthsAgo);
      startDate = sixMonthsAgo;
    }

    if (endDate.isBefore(startDate)) {
      log.warn(
          "Calculated endDate {} is before startDate {}. This might happen if the job runs just after midnight before the first transaction of the day or due to capping. Skipping synchronization for ISIN {} for this run.",
          endDate,
          startDate,
          isin);
      return;
    }

    log.info("Synchronizing fund transactions for ISIN {} from {} to {}", isin, startDate, endDate);
    try {
      fundTransactionSynchronizer.sync(isin, startDate, endDate);
      log.info(
          "Scheduled fund transaction synchronization job completed successfully for ISIN {}.",
          isin);
    } catch (Exception e) {
      log.error(
          "Scheduled fund transaction synchronization job failed during execution for ISIN {}: {}",
          isin,
          e.getMessage(),
          e);
    }
  }
}
