package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.event.RunOcfCalculationRequested;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class OcfCalculationJob {

  private final OcfCalculationService service;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = "0 0 9 3-8 * *", zone = TIMEZONE)
  @SchedulerLock(name = "OcfCalculationJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
  void computeMonthlyIfReady() {
    var today = LocalDate.now(clock);
    if (!isNthBusinessDayOfMonth(today, 4)) {
      return;
    }
    var lastMonth = YearMonth.from(today).minusMonths(1);
    log.info("Computing monthly OCF: period={}", lastMonth);
    service.calculateForAllFunds(lastMonth);
  }

  @EventListener(RunOcfCalculationRequested.class)
  void onOcfCalculationRequested() {
    var lastMonth = YearMonth.now(clock).minusMonths(1);
    log.info("OCF calculation requested: period={}", lastMonth);
    service.calculateForAllFunds(lastMonth);
  }

  boolean isNthBusinessDayOfMonth(LocalDate date, int n) {
    var nthBusinessDay = date.withDayOfMonth(1);
    if (!publicHolidays.isWorkingDay(nthBusinessDay)) {
      nthBusinessDay = publicHolidays.nextWorkingDay(nthBusinessDay);
    }
    for (int i = 1; i < n; i++) {
      nthBusinessDay = publicHolidays.nextWorkingDay(nthBusinessDay);
    }
    return date.equals(nthBusinessDay);
  }
}
