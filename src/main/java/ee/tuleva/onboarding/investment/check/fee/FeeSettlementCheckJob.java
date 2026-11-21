package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.investment.event.RunFeeSettlementCheckRequested;
import ee.tuleva.onboarding.investment.fees.OutstandingFees;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
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
class FeeSettlementCheckJob {

  private static final int SETTLEMENT_SETTLED_BY_BUSINESS_DAY = 3;

  private final FeeCheckService feeCheckService;
  private final OutstandingFees outstandingFees;
  private final BusinessDays businessDays;
  private final Clock clock;

  // The third business day, not the second. Month m's fee is settled by the NAV run whose position
  // date is the day its debit was booked: the second business day's run (11:00 for TUK75/TUK00,
  // 15:20 for TUV100/TKF100) when the depositary debits on the first. Checking at 10:00 on the
  // second business day would find every fund's month still awaiting its debit.
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
    var feeMonths = monthsStillSettledByTheirDebitsThrough(settlementMonth);
    log.info("Starting fee settlement check: feeMonths={}", feeMonths);
    try {
      feeCheckService.runMonthlyChecks(List.of(TulevaFund.values()), feeMonths, checkDate);
      log.info("Fee settlement check completed: settlementMonth={}", settlementMonth);
    } catch (Exception e) {
      log.error("Fee settlement check failed: settlementMonth={}", settlementMonth, e);
    }
  }

  private List<LocalDate> monthsStillSettledByTheirDebitsThrough(LocalDate settlementMonth) {
    var firstHeldMonth = outstandingFees.firstFeeMonthSettledByPayment();
    var firstMonth = firstHeldMonth.isBefore(settlementMonth) ? firstHeldMonth : settlementMonth;
    return Stream.iterate(
            firstMonth, month -> !month.isAfter(settlementMonth), month -> month.plusMonths(1))
        .toList();
  }
}
