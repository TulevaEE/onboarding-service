package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.time.Month;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledFundBalanceSynchronizationJob {

  private final FundBalanceSynchronizer fundBalanceSynchronizer;

  @Scheduled(cron = "0 10 4 * * ?", zone = "Europe/Tallinn")
  public void runDailySync() {
    LocalDate syncDate = LocalDate.now(ClockHolder.clock()).minusDays(1);
    log.info("Starting scheduled fund balance synchronization job for previous day {}.", syncDate);
    try {
      fundBalanceSynchronizer.sync(syncDate);
      log.info(
          "Scheduled fund balance synchronization job completed successfully for previous day {}.",
          syncDate);
    } catch (Exception e) {
      log.error(
          "Scheduled fund balance synchronization job failed during execution for previous day {}: {}",
          syncDate,
          e.getMessage(),
          e);
    }
  }

  @Scheduled(cron = "0 10 10 23 4 ?", zone = "Europe/Tallinn")
  public void runInitialFundBalanceSync() {
    LocalDate syncDate = LocalDate.now(ClockHolder.clock()).minusDays(1);
    log.info(
        "Starting initial scheduled fund balance synchronization job for previous day {}.",
        syncDate);
    try {
      fundBalanceSynchronizer.sync(syncDate);
      log.info(
          "Initial scheduled fund balance synchronization job completed successfully for previous day {}.",
          syncDate);
    } catch (Exception e) {
      log.error(
          "Initial scheduled fund balance synchronization job failed during execution for previous day {}: {}",
          syncDate,
          e.getMessage(),
          e);
    }
  }

  @Scheduled(cron = "0 15 13 6 5 ?", zone = "Europe/Tallinn")
  public void runHistoricalFundBalanceSync() {
    LocalDate startDate = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate endDate = LocalDate.of(2025, Month.APRIL, 21);
    log.info(
        "Starting historical fund balance synchronization job from {} to {}.", startDate, endDate);

    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      try {
        log.debug("Synchronizing fund balances for date {}.", date);
        fundBalanceSynchronizer.sync(date);
        log.debug("Successfully synchronized fund balances for date {}.", date);
      } catch (Exception e) {
        log.error(
            "Historical fund balance synchronization failed during execution for date {}: {}",
            date,
            e.getMessage(),
            e);
      }
    }
    log.info(
        "Finished historical fund balance synchronization job from {} to {}.", startDate, endDate);
  }
}
