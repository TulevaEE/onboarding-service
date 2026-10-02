package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.JobRunSchedule.CASH_BUFFER_REVIEW_BEFORE_THE_MORNING_IMPORTS;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.deadline.BusinessDays;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
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
class CashBufferReviewJob {

  private static final int FOURTH_BUSINESS_DAY_WITH_OCF_AND_TD_ONCE_LAST_MONTHS_FEES_SETTLED = 4;

  private final CashBufferReviewService service;
  private final BusinessDays businessDays;
  private final Clock clock;

  @Scheduled(cron = CASH_BUFFER_REVIEW_BEFORE_THE_MORNING_IMPORTS, zone = TIMEZONE)
  @SchedulerLock(name = "CashBufferReviewJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
  void reviewTheClosedMonthIfDue() {
    var today = LocalDate.now(clock);
    if (!businessDays.isNthBusinessDayOfMonth(
        today, FOURTH_BUSINESS_DAY_WITH_OCF_AND_TD_ONCE_LAST_MONTHS_FEES_SETTLED)) {
      return;
    }
    var closedMonth = YearMonth.from(today).minusMonths(1);
    log.info("Starting cash buffer review: reviewMonth={}, reviewedOn={}", closedMonth, today);
    service.reviewAllFunds(closedMonth, today);
  }
}
