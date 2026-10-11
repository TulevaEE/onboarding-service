package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionCutoff.CUTOFF_TIME;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionCutoff.TALLINN;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

  LocalDate warnFrom(Instant requestedAt) {
    return publicHolidays.addWorkingDays(orderDay(requestedAt), WARN_AFTER_WORKING_DAYS);
  }

  LocalDate bound(Instant requestedAt) {
    return publicHolidays.addWorkingDays(orderDay(requestedAt), BOUND_WORKING_DAYS);
  }
}
