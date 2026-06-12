package ee.tuleva.onboarding.investment.calendar;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.Month.AUGUST;
import static java.time.Month.DECEMBER;
import static java.time.Month.FEBRUARY;
import static java.time.Month.JANUARY;
import static java.time.Month.JULY;
import static java.time.Month.JUNE;
import static java.time.Month.MARCH;
import static java.time.Month.MAY;
import static java.time.Month.NOVEMBER;
import static java.time.Month.OCTOBER;
import static java.time.temporal.TemporalAdjusters.firstInMonth;
import static java.time.temporal.TemporalAdjusters.lastInMonth;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomicileCalendar {

  private final Target2Calendar target2Calendar;
  private final Map<Domicile, Map<Integer, Set<LocalDate>>> holidaysByDomicileAndYear =
      new ConcurrentHashMap<>();

  public TradingCalendar forDomicile(Domicile domicile) {
    return date ->
        target2Calendar.isBusinessDay(date) && !holidays(domicile, date.getYear()).contains(date);
  }

  private Set<LocalDate> holidays(Domicile domicile, int year) {
    return holidaysByDomicileAndYear
        .computeIfAbsent(domicile, ignored -> new ConcurrentHashMap<>())
        .computeIfAbsent(year, ignored -> publicHolidays(domicile, year));
  }

  private static Set<LocalDate> publicHolidays(Domicile domicile, int year) {
    return switch (domicile) {
      case IRELAND -> irishHolidays(year);
      case LUXEMBOURG -> luxembourgHolidays(year);
      case FRANCE -> frenchHolidays(year);
    };
  }

  private static Set<LocalDate> irishHolidays(int year) {
    LocalDate easterSunday = EasterDates.easterSunday(year);
    Set<LocalDate> holidays =
        new HashSet<>(
            Set.of(
                newYearsDay(year),
                stBrigidsDay(year),
                stPatricksDay(year),
                easterMonday(easterSunday),
                firstMonday(year, MAY),
                firstMonday(year, JUNE),
                firstMonday(year, AUGUST),
                lastMonday(year, OCTOBER),
                christmasDay(year),
                stStephensDay(year)));
    addWeekendSubstitutes(
        holidays, newYearsDay(year), stPatricksDay(year), christmasDay(year), stStephensDay(year));
    return Set.copyOf(holidays);
  }

  private static void addWeekendSubstitutes(Set<LocalDate> holidays, LocalDate... fixedHolidays) {
    for (LocalDate fixedHoliday : fixedHolidays) {
      if (isWeekend(fixedHoliday)) {
        holidays.add(nextFreeWeekday(fixedHoliday, holidays));
      }
    }
  }

  private static LocalDate nextFreeWeekday(LocalDate date, Set<LocalDate> holidays) {
    LocalDate candidate = date.plusDays(1);
    while (isWeekend(candidate) || holidays.contains(candidate)) {
      candidate = candidate.plusDays(1);
    }
    return candidate;
  }

  private static boolean isWeekend(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day == SATURDAY || day == SUNDAY;
  }

  private static LocalDate stBrigidsDay(int year) {
    LocalDate februaryFirst = LocalDate.of(year, FEBRUARY, 1);
    return februaryFirst.getDayOfWeek() == FRIDAY ? februaryFirst : firstMonday(year, FEBRUARY);
  }

  private static LocalDate firstMonday(int year, Month month) {
    return LocalDate.of(year, month, 1).with(firstInMonth(MONDAY));
  }

  private static LocalDate lastMonday(int year, Month month) {
    return LocalDate.of(year, month, 1).with(lastInMonth(MONDAY));
  }

  private static Set<LocalDate> luxembourgHolidays(int year) {
    LocalDate easterSunday = EasterDates.easterSunday(year);
    return Set.of(
        newYearsDay(year),
        easterMonday(easterSunday),
        labourDay(year),
        europeDay(year),
        ascensionDay(easterSunday),
        whitMonday(easterSunday),
        luxembourgNationalDay(year),
        assumptionDay(year),
        allSaintsDay(year),
        christmasDay(year),
        stStephensDay(year));
  }

  private static Set<LocalDate> frenchHolidays(int year) {
    LocalDate easterSunday = EasterDates.easterSunday(year);
    return Set.of(
        newYearsDay(year),
        easterMonday(easterSunday),
        labourDay(year),
        victoryDay(year),
        ascensionDay(easterSunday),
        whitMonday(easterSunday),
        bastilleDay(year),
        assumptionDay(year),
        allSaintsDay(year),
        armisticeDay(year),
        christmasDay(year));
  }

  private static LocalDate newYearsDay(int year) {
    return LocalDate.of(year, JANUARY, 1);
  }

  private static LocalDate stPatricksDay(int year) {
    return LocalDate.of(year, MARCH, 17);
  }

  private static LocalDate easterMonday(LocalDate easterSunday) {
    return easterSunday.plusDays(1);
  }

  private static LocalDate labourDay(int year) {
    return LocalDate.of(year, MAY, 1);
  }

  private static LocalDate victoryDay(int year) {
    return LocalDate.of(year, MAY, 8);
  }

  private static LocalDate europeDay(int year) {
    return LocalDate.of(year, MAY, 9);
  }

  private static LocalDate ascensionDay(LocalDate easterSunday) {
    return easterSunday.plusDays(39);
  }

  private static LocalDate whitMonday(LocalDate easterSunday) {
    return easterSunday.plusDays(50);
  }

  private static LocalDate luxembourgNationalDay(int year) {
    return LocalDate.of(year, JUNE, 23);
  }

  private static LocalDate bastilleDay(int year) {
    return LocalDate.of(year, JULY, 14);
  }

  private static LocalDate assumptionDay(int year) {
    return LocalDate.of(year, AUGUST, 15);
  }

  private static LocalDate allSaintsDay(int year) {
    return LocalDate.of(year, NOVEMBER, 1);
  }

  private static LocalDate armisticeDay(int year) {
    return LocalDate.of(year, NOVEMBER, 11);
  }

  private static LocalDate christmasDay(int year) {
    return LocalDate.of(year, DECEMBER, 25);
  }

  private static LocalDate stStephensDay(int year) {
    return LocalDate.of(year, DECEMBER, 26);
  }
}
