package ee.tuleva.onboarding.fund.fees;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
class SavingsFundManagementFeeSyncJob {

  private static final String TIMEZONE = "Europe/Tallinn";

  private final SavingsFundManagementFeeSync savingsFundManagementFeeSync;
  private final Clock clock;

  @Scheduled(cron = "0 7 0 * * *", zone = TIMEZONE)
  @SchedulerLock(
      name = "SavingsFundManagementFeeSyncJob_sync",
      lockAtMostFor = "5m",
      lockAtLeastFor = "1m")
  void sync() {
    var today = LocalDate.now(clock.withZone(ZoneId.of(TIMEZONE)));
    try {
      savingsFundManagementFeeSync.syncToTheRatesInForceOn(today);
    } catch (Exception e) {
      log.error("Failed to sync savings fund management fees: date={}", today, e);
    }
  }
}
