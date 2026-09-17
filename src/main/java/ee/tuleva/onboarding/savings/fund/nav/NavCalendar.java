package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NavCalendar {

  private final PublicHolidays publicHolidays;

  public LocalDate calculationDateOf(LocalDate navDate) {
    return publicHolidays.nextWorkingDay(navDate);
  }
}
