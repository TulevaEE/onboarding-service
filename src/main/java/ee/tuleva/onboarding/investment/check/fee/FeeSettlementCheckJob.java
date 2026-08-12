package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunFeeSettlementCheckRequested;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
class FeeSettlementCheckJob {

  private static final int SETTLEMENT_SETTLED_BY_BUSINESS_DAY = 3;

  private final FeeCheckService feeCheckService;
  private final BusinessDays businessDays;
  private final Clock clock;

  // The third business day, not the second. A NAV run on the first business day of m+1 still uses
  // the last working day of m as its position date, so no fee month boundary is crossed. The
  // crossing - and with it month m's settlement posting - happens during the second business day's
  // run, at each fund's cutoff (11:00 for TUK75/TUK00, 15:20 for TUV100/TKF100). Checking at 10:00
  // on the second business day would read a residual for every fund, every month.
  @Scheduled(cron = "0 0 10 1-14 * *", zone = TIMEZONE)
  @SchedulerLock(name = "FeeSettlementCheckJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
  void checkClosedMonthIfReady() {
    var today = LocalDate.now(clock);
    if (!businessDays.isOnOrAfterNthBusinessDayOfMonth(today, SETTLEMENT_SETTLED_BY_BUSINESS_DAY)) {
      return;
    }
    runForClosedMonth(today);
  }

  @EventListener(RunFeeSettlementCheckRequested.class)
  void onSettlementCheckRequested() {
    runForClosedMonth(LocalDate.now(clock));
  }

  private void runForClosedMonth(LocalDate checkDate) {
    var settlementMonth = checkDate.withDayOfMonth(1).minusMonths(1);
    var cashMonth = settlementMonth.minusMonths(1);
    log.info(
        "Starting fee settlement check: settlementMonth={}, cashMonth={}",
        settlementMonth,
        cashMonth);
    try {
      feeCheckService.runMonthlyChecks(
          List.of(TulevaFund.values()), settlementMonth, cashMonth, checkDate);
      log.info("Fee settlement check completed: settlementMonth={}", settlementMonth);
    } catch (Exception e) {
      log.error("Fee settlement check failed: settlementMonth={}", settlementMonth, e);
    }
  }
}
