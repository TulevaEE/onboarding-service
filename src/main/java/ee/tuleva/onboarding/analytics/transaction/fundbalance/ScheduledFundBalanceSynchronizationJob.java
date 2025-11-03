package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledFundBalanceSynchronizationJob {

  private final FundBalanceSynchronizer fundBalanceSynchronizer;

  @Schedules({
    @Scheduled(cron = "0 0 11 3 11 ?", zone = "Europe/Tallinn"),
    @Scheduled(cron = "0 0 18 * * ?", zone = "Europe/Tallinn")
  })
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
}
