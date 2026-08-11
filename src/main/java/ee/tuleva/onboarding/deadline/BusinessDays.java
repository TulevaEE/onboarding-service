package ee.tuleva.onboarding.deadline;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessDays {

  private final PublicHolidays publicHolidays;

  public boolean isNthBusinessDayOfMonth(LocalDate date, int n) {
    return date.equals(nthBusinessDayOfMonth(date, n));
  }

  public boolean isOnOrAfterNthBusinessDayOfMonth(LocalDate date, int n) {
    return !date.isBefore(nthBusinessDayOfMonth(date, n));
  }

  public LocalDate nthBusinessDayOfMonth(LocalDate dayInMonth, int n) {
    var nthBusinessDay = dayInMonth.withDayOfMonth(1);
    if (!publicHolidays.isWorkingDay(nthBusinessDay)) {
      nthBusinessDay = publicHolidays.nextWorkingDay(nthBusinessDay);
    }
    for (int i = 1; i < n; i++) {
      nthBusinessDay = publicHolidays.nextWorkingDay(nthBusinessDay);
    }
    return nthBusinessDay;
  }
}
