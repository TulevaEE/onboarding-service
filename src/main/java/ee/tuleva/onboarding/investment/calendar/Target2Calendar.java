package ee.tuleva.onboarding.investment.calendar;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.Month.DECEMBER;
import static java.time.Month.JANUARY;
import static java.time.Month.MAY;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class Target2Calendar implements TradingCalendar {

  @Override
  public boolean isBusinessDay(LocalDate date) {
    return !isWeekend(date) && !isClosingDay(date);
  }

  private static boolean isWeekend(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day == SATURDAY || day == SUNDAY;
  }

  private static boolean isClosingDay(LocalDate date) {
    LocalDate easterSunday = EasterDates.easterSunday(date.getYear());
    return Set.of(
            LocalDate.of(date.getYear(), JANUARY, 1),
            easterSunday.minusDays(2),
            easterSunday.plusDays(1),
            LocalDate.of(date.getYear(), MAY, 1),
            LocalDate.of(date.getYear(), DECEMBER, 25),
            LocalDate.of(date.getYear(), DECEMBER, 26))
        .contains(date);
  }
}
