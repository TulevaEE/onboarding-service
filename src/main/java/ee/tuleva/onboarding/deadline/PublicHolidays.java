package ee.tuleva.onboarding.deadline;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PublicHolidays {

  public LocalDate addWorkingDays(LocalDate date, int workingDays) {
    if (workingDays == 0) {
      return date;
    }
    return addWorkingDays(nextWorkingDay(date), --workingDays);
  }

  public LocalDate nextWorkingDay(LocalDate to) {
    LocalDate date = to.plusDays(1);
    while (!isWorkingDay(date)) {
      date = date.plusDays(1);
    }
    return date;
  }

  public LocalDate previousWorkingDay(LocalDate to) {
    LocalDate date = to.minusDays(1);
    while (!isWorkingDay(date)) {
      date = date.minusDays(1);
    }
    return date;
  }

  public boolean isWorkingDay(LocalDate date) {
    return !(date.getDayOfWeek() == SATURDAY
        || date.getDayOfWeek() == SUNDAY
        || isPublicHoliday(date));
  }

  boolean isPublicHoliday(LocalDate date) {
    return List.of(
            newYearsDay(date),
            independenceDay(date),
            goodFriday(date),
            easterSunday(date),
            springDay(date),
            pentecost(date),
            victoryDay(date),
            midsummerDay(date),
            dayOfRestorationOfIndependence(date),
            christmasEve(date),
            christmasDay(date),
            boxingDay(date))
        .contains(date);
  }

  LocalDate newYearsDay(LocalDate date) {
    return date.withDayOfMonth(1).withMonth(1);
  }

  LocalDate independenceDay(LocalDate date) {
    return date.withDayOfMonth(24).withMonth(2);
  }

  LocalDate goodFriday(LocalDate date) {
    return easterSunday(date).minusDays(2);
  }

  LocalDate easterSunday(LocalDate date) {
    return getEasterSundayDate(date.getYear());
  }

  LocalDate springDay(LocalDate date) {
    return date.withDayOfMonth(1).withMonth(5);
  }

  LocalDate pentecost(LocalDate date) {
    return easterSunday(date).plusDays(49);
  }

  LocalDate victoryDay(LocalDate date) {
    return date.withDayOfMonth(23).withMonth(6);
  }

  LocalDate midsummerDay(LocalDate date) {
    return date.withDayOfMonth(24).withMonth(6);
  }

  LocalDate dayOfRestorationOfIndependence(LocalDate date) {
    return date.withDayOfMonth(20).withMonth(8);
  }

  LocalDate christmasEve(LocalDate date) {
    return date.withDayOfMonth(24).withMonth(12);
  }

  LocalDate christmasDay(LocalDate date) {
    return date.withDayOfMonth(25).withMonth(12);
  }

  LocalDate boxingDay(LocalDate date) {
    return date.withDayOfMonth(26).withMonth(12);
  }

  private LocalDate getEasterSundayDate(int year) {
    int a = year % 19;
    int b = year / 100;
    int c = year % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int month = (h + l - 7 * m + 114) / 31;
    int day = ((h + l - 7 * m + 114) % 31) + 1;
    return LocalDate.of(year, month, day);
  }
}
