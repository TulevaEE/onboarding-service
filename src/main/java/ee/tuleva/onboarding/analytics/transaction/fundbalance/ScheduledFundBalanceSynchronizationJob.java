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

  @Scheduled(cron = "0 0 18 * * ?", zone = "Europe/Tallinn")
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

  @Scheduled(cron = "0 20 10 16 5 ?", zone = "Europe/Tallinn")
  public void runHistoricalFundBalanceSync() {
    LocalDate startDate = LocalDate.of(2025, Month.APRIL, 22);
    LocalDate endDate = LocalDate.of(2025, Month.MAY, 16);
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
