package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunTdAttributionBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTdAttributionMonthlyRequested;
import ee.tuleva.onboarding.investment.event.RunTdAttributionRequested;
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
public class PeriodicTdAttributionJob {

  private final PeriodicTdAttributionService service;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = "0 0 8 3-8 * *", zone = TIMEZONE)
  @SchedulerLock(
      name = "PeriodicTdAttributionJob",
      lockAtMostFor = "PT30M",
      lockAtLeastFor = "PT5M")
  void computeMonthlyIfReady() {
    var today = LocalDate.now(clock);
    if (!isNthBusinessDayOfMonth(today, 4)) {
      return;
    }
    var lastMonth = YearMonth.from(today).minusMonths(1);
    log.info("Computing monthly TD attribution: period={}", lastMonth);
    service.computeForAllFunds(lastMonth.atDay(1), lastMonth.atEndOfMonth(), PeriodType.MONTHLY);
  }

  @EventListener
  void onAttributionRequested(RunTdAttributionRequested event) {
    log.info(
        "TD attribution requested: fund={}, period={}-{}, type={}",
        event.fundCode(),
        event.periodStart(),
        event.periodEnd(),
        event.periodType());
    var fund = TulevaFund.valueOf(event.fundCode());
    service.computeAttribution(
        fund, event.periodStart(), event.periodEnd(), PeriodType.valueOf(event.periodType()));
  }

  @EventListener
  void onMonthlyRequested(RunTdAttributionMonthlyRequested event) {
    var lastMonth = YearMonth.now(clock).minusMonths(1);
    log.info("TD attribution monthly requested: period={}", lastMonth);
    service.computeForAllFunds(lastMonth.atDay(1), lastMonth.atEndOfMonth(), PeriodType.MONTHLY);
  }

  @EventListener
  void onBackfillRequested(RunTdAttributionBackfillRequested event) {
    log.info("TD attribution backfill requested: monthsBack={}", event.monthsBack());
    service.backfillMonths(event.monthsBack(), clock);
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
