package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.JobRunSchedule.OWNERSHIP_LIMIT_CHECK;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.investment.event.RunOwnershipLimitCheckRequested;
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
class OwnershipLimitCheckJob {

  private static final int MONTHLY_RUN_BUSINESS_DAY = 4;

  private final OwnershipLimitCheckService service;
  private final OwnershipLimitCheckNotifier notifier;
  private final BusinessDays businessDays;
  private final Clock clock;

  @Scheduled(cron = OWNERSHIP_LIMIT_CHECK, zone = TIMEZONE)
  @SchedulerLock(name = "OwnershipLimitCheckJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
  void checkClosedMonthUntilEveryFundIsChecked() {
    var today = LocalDate.now(clock);
    if (!businessDays.isOnOrAfterNthBusinessDayOfMonth(today, MONTHLY_RUN_BUSINESS_DAY)) {
      return;
    }
    var closedMonth = YearMonth.from(today).minusMonths(1);
    reportingAFailure(
        closedMonth,
        () -> {
          if (!service.everyFundIsChecked(closedMonth)) {
            checkMonthEnd(closedMonth);
          }
        });
  }

  @EventListener(RunOwnershipLimitCheckRequested.class)
  void onOwnershipLimitCheckRequested() {
    var closedMonth = YearMonth.now(clock).minusMonths(1);
    reportingAFailure(closedMonth, () -> checkMonthEnd(closedMonth));
  }

  private void reportingAFailure(YearMonth month, Runnable check) {
    try {
      check.run();
    } catch (Exception e) {
      log.error("Ownership limit check failed: month={}", month, e);
      notifier.notifyFailed(month, e);
    }
  }

  private void checkMonthEnd(YearMonth month) {
    log.info("Starting ownership limit check: month={}", month);
    var run = service.checkMonthEnd(month);
    notifier.notify(run);
    log.info(
        "Ownership limit check completed: month={}, worstSeverity={}", month, run.worstSeverity());
  }
}
