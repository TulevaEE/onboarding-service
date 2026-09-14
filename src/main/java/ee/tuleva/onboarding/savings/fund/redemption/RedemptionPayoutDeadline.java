package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionCutoff.CUTOFF_TIME;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionCutoff.TALLINN;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * When a redemption payout has to have been initiated.
 *
 * <p>Sisekord 16 § 1.1.3: Tuleva initiates the payout to the unit holder's account no later than
 * T+3, counted from the day the order was given. The value date is T+1, so the bound is only two
 * working days after it — reading T+3 from the value date is a day too generous, and that mistake
 * has been made once already.
 *
 * <p>The cut-off moves T itself: an order from 16:00 onward belongs to the next working day, so the
 * whole bound shifts with it.
 */
@Component
@RequiredArgsConstructor
class RedemptionPayoutDeadline {

  private static final int WARN_AFTER_WORKING_DAYS = 2;
  private static final int BOUND_WORKING_DAYS = 3;

  private final PublicHolidays publicHolidays;

  LocalDate orderDay(Instant requestedAt) {
    var ordered = requestedAt.atZone(TALLINN);
    var date = ordered.toLocalDate();
    if (publicHolidays.isWorkingDay(date) && ordered.toLocalTime().isBefore(CUTOFF_TIME)) {
      return date;
    }
    return publicHolidays.nextWorkingDay(date);
  }

  /**
   * A working day before the bound, so there is still a day to act rather than a breach to report.
   */
  LocalDate warnFrom(Instant requestedAt) {
    return publicHolidays.addWorkingDays(orderDay(requestedAt), WARN_AFTER_WORKING_DAYS);
  }

  LocalDate bound(Instant requestedAt) {
    return publicHolidays.addWorkingDays(orderDay(requestedAt), BOUND_WORKING_DAYS);
  }
}
