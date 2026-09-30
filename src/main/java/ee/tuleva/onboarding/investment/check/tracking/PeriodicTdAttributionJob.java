package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TD_ATTRIBUTION_SELF_HEAL;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.investment.event.RunTdAttributionBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTdAttributionMonthlyRequested;
import ee.tuleva.onboarding.investment.event.RunTdAttributionRequested;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;
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
class PeriodicTdAttributionJob {

  private static final int MONTHLY_RUN_BUSINESS_DAY = 4;
  private static final int SELF_HEAL_MONTHS = 12;

  private final PeriodicTdAttributionService service;
  private final BusinessDays businessDays;
  private final Clock clock;

  @Scheduled(cron = "0 0 8 1-14 * *", zone = TIMEZONE)
  @SchedulerLock(
      name = "PeriodicTdAttributionJob",
      lockAtMostFor = "PT30M",
      lockAtLeastFor = "PT5M")
  void computeMonthlyIfReady() {
    var today = LocalDate.now(clock);
    if (!businessDays.isNthBusinessDayOfMonth(today, MONTHLY_RUN_BUSINESS_DAY)) {
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

  @EventListener(RunTdAttributionMonthlyRequested.class)
  void onMonthlyRequested() {
    var lastMonth = YearMonth.now(clock).minusMonths(1);
    log.info("TD attribution monthly requested: period={}", lastMonth);
    service.computeForAllFunds(lastMonth.atDay(1), lastMonth.atEndOfMonth(), PeriodType.MONTHLY);
  }

  @Scheduled(cron = TD_ATTRIBUTION_SELF_HEAL, zone = TIMEZONE)
  @SchedulerLock(name = "TdAttributionSelfHeal", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
  void rewriteStaleMonths() {
    service.rewriteStaleMonths(monthsPastTheirMonthlyRun(LocalDate.now(clock)));
  }

  private List<YearMonth> monthsPastTheirMonthlyRun(LocalDate today) {
    var lastMonth = YearMonth.from(today).minusMonths(1);
    var lastMonthWasRun =
        businessDays.isOnOrAfterNthBusinessDayOfMonth(today, MONTHLY_RUN_BUSINESS_DAY);
    return Stream.iterate(lastMonth.minusMonths(SELF_HEAL_MONTHS - 1), month -> month.plusMonths(1))
        .limit(SELF_HEAL_MONTHS)
        .filter(month -> lastMonthWasRun || !month.equals(lastMonth))
        .toList();
  }

  @EventListener
  void onBackfillRequested(RunTdAttributionBackfillRequested event) {
    log.info("TD attribution backfill requested: monthsBack={}", event.monthsBack());
    service.backfillMonths(event.monthsBack(), clock);
  }
}
