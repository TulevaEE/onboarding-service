package ee.tuleva.onboarding.deadline;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PublicHolidays {

  private final Clock estonianClock;

  public boolean isPublicHoliday(LocalDate date) {
    return Arrays.asList(
            newYearsDay(),
            independenceDay(),
            goodFriday(),
            easterSunday(),
            springDay(),
            pentecost(),
            victoryDay(),
            midsummerDay(),
            dayOfRestorationOfIndependence(),
            christmasEve(),
            christmasDay(),
            boxingDay())
        .contains(date);
  }

  LocalDate newYearsDay() {
    return now().withDayOfMonth(1).withMonth(1);
  }

  LocalDate independenceDay() {
    return now().withDayOfMonth(24).withMonth(2);
  }

  LocalDate goodFriday() {
    return easterSunday().minusDays(2);
  }

  LocalDate easterSunday() {
    return getEasterSundayDate(now().getYear());
  }

  LocalDate springDay() {
    return now().withDayOfMonth(1).withMonth(5);
  }

  LocalDate pentecost() {
    return easterSunday().plusDays(49);
  }

  LocalDate victoryDay() {
    return now().withDayOfMonth(23).withMonth(6);
  }

  LocalDate midsummerDay() {
    return now().withDayOfMonth(24).withMonth(6);
  }

  LocalDate dayOfRestorationOfIndependence() {
    return now().withDayOfMonth(20).withMonth(8);
  }

  LocalDate christmasEve() {
    return now().withDayOfMonth(24).withMonth(12);
  }

  LocalDate christmasDay() {
    return now().withDayOfMonth(25).withMonth(12);
  }

  LocalDate boxingDay() {
    return now().withDayOfMonth(26).withMonth(12);
  }

  private LocalDate now() {
    return LocalDate.now(estonianClock);
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
