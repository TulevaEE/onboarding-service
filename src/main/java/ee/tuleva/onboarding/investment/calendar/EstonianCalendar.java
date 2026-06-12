package ee.tuleva.onboarding.investment.calendar;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EstonianCalendar implements TradingCalendar {

  private final PublicHolidays publicHolidays;

  @Override
  public boolean isBusinessDay(LocalDate date) {
    return publicHolidays.isWorkingDay(date);
  }
}
