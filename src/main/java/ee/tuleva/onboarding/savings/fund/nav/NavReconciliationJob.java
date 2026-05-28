package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
class NavReconciliationJob {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final NavReconciliationService reconciliationService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = "0 10 16 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavReconciliationJob", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void reconcileDaily() {
    LocalDate today = ZonedDateTime.now(clock).withZoneSameInstant(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }
    LocalDate navDate = publicHolidays.previousWorkingDay(today);
    log.info("Running CSV-to-API reconciliation: navDate={}", navDate);
    reconciliationService.reconcile(navDate);
  }
}
