package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("production")
@RequiredArgsConstructor
class SebTradeListingCheckJob {

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final SebTradeListingCheck check;

  @Scheduled(cron = "0 15 10 * * MON-FRI", zone = TIMEZONE)
  @SchedulerLock(name = "SebTradeListingCheckJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);
    if (!publicHolidays.isWorkingDay(today)) {
      log.info("SEB trade listing check skipped on a public holiday: today={}", today);
      return;
    }
    check.run();
  }
}
