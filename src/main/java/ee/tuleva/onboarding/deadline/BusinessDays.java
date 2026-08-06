package ee.tuleva.onboarding.deadline;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessDays {

  private final PublicHolidays publicHolidays;

  public boolean isNthBusinessDayOfMonth(LocalDate date, int n) {
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
