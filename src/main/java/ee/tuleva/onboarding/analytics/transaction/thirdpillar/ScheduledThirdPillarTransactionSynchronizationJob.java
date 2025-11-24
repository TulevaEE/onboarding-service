package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

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
public class ScheduledThirdPillarTransactionSynchronizationJob {

  private final ThirdPillarTransactionSynchronizer thirdPillarTransactionSynchronizer;
  private final AnalyticsThirdPillarTransactionRepository transactionRepository;
  private static final int DEFAULT_LOOKBACK_DAYS = 2;

  @Scheduled(cron = "0 0 2 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledThirdPillarTransactionSynchronizationJob_runDailySync",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void runDailySync() {
    log.info("Starting third pillar transactions synchronization job");

    LocalDate endDate = LocalDate.now(ClockHolder.clock());

    Optional<LocalDate> latestReportingDateOpt = transactionRepository.findLatestReportingDate();
    LocalDate startDate =
        latestReportingDateOpt.orElseGet(
            () -> {
              log.warn(
                  "No existing reporting date found for third pillar transactions. Falling back to synchronizing the last {} days.",
                  DEFAULT_LOOKBACK_DAYS);
              return endDate.minusDays(DEFAULT_LOOKBACK_DAYS);
            });

    if (endDate.isBefore(startDate)) {
      log.warn(
          "Calculated endDate {} is before startDate {}. Skipping third pillar synchronization.",
          endDate,
          startDate);
      return;
    }

    log.info("Synchronizing third pillar transactions from {} to {}", startDate, endDate);
    try {
      thirdPillarTransactionSynchronizer.sync(startDate, endDate);
      log.info("Third pillar transactions synchronization job completed");
    } catch (Exception e) {
      log.error("Third pillar transaction synchronization job failed: {}", e.getMessage(), e);
    }
  }
}
