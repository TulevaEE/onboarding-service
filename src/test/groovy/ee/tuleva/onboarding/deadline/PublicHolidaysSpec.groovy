package ee.tuleva.onboarding.deadline

import spock.lang.Specification

import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

class PublicHolidaysSpec extends Specification {

  def date = LocalDate.parse("2021-03-11")

  def "can get estonian public holidays"() {
    when:
    PublicHolidays publicHolidays = new PublicHolidays()

    then:
    with(publicHolidays) {
      LocalDate.parse("2021-01-01") == newYearsDay(date)
      LocalDate.parse("2021-02-24") == independenceDay(date)
      LocalDate.parse("2021-04-02") == goodFriday(date)
      LocalDate.parse("2021-04-04") == easterSunday(date)
      LocalDate.parse("2021-05-01") == springDay(date)
      LocalDate.parse("2021-05-23") == pentecost(date)
      LocalDate.parse("2021-06-23") == victoryDay(date)
      LocalDate.parse("2021-06-24") == midsummerDay(date)
      LocalDate.parse("2021-08-20") == dayOfRestorationOfIndependence(date)
      LocalDate.parse("2021-12-24") == christmasEve(date)
      LocalDate.parse("2021-12-25") == christmasDay(date)
      LocalDate.parse("2021-12-26") == boxingDay(date)
    }
  }

  def "christmas day is not working day"() {
    when:
    PublicHolidays publicHolidays = new PublicHolidays()
    then:
    !publicHolidays.isWorkingDay(publicHolidays.christmasDay(date))
  }

  def "next workday from christmas day 2021 is 27th"() {
    when:
    PublicHolidays publicHolidays = new PublicHolidays()
    then:
    publicHolidays.nextWorkingDay(publicHolidays.christmasDay(date)) ==
        LocalDate.of(2021, Month.DECEMBER, 27)
  }

  def "three working days after christmas day, christmas eve or boxing day 2021 is wednesday"() {
    given:
    LocalDate threeWorkingDaysAfterChristmas = LocalDate.of(2021, Month.DECEMBER, 29)
    when:
    PublicHolidays publicHolidays = new PublicHolidays()
    then:
    publicHolidays.addWorkingDays(publicHolidays.christmasDay(date), 3) == threeWorkingDaysAfterChristmas
    publicHolidays.addWorkingDays(publicHolidays.boxingDay(date), 3) == threeWorkingDaysAfterChristmas
    publicHolidays.addWorkingDays(publicHolidays.christmasEve(date), 3) == threeWorkingDaysAfterChristmas
  }

  def "previous workday for may 2 is april 28"() {
    when:
    PublicHolidays publicHolidays = new PublicHolidays()
    then:
    publicHolidays.previousWorkingDay(LocalDate.parse("2017-05-02")) == LocalDate.parse("2017-04-28")
  }

  def "nth business day of month skips weekends and public holidays"() {
    given:
    PublicHolidays publicHolidays = new PublicHolidays()
    expect:
    publicHolidays.nthBusinessDayOfMonth(YearMonth.from(LocalDate.parse(month)), n) == LocalDate.parse(expected)
    where:
    month        | n || expected
    "2026-02-01" | 1 || "2026-02-02"
    "2026-02-01" | 4 || "2026-02-05"
    "2026-05-01" | 4 || "2026-05-07"
    "2026-06-01" | 4 || "2026-06-04"
    "2026-08-01" | 4 || "2026-08-06"
  }

  def "isNthBusinessDayOfMonth is true only on the exact day"() {
    given:
    PublicHolidays publicHolidays = new PublicHolidays()
    expect:
    publicHolidays.isNthBusinessDayOfMonth(LocalDate.parse(date), 4) == expected
    where:
    date         || expected
    "2026-02-05" || true
    "2026-02-04" || false
    "2026-02-06" || false
    "2026-05-07" || true
    "2026-05-06" || false
  }

  def "isOnOrAfterNthBusinessDayOfMonth is true from the nth business day onwards"() {
    given:
    PublicHolidays publicHolidays = new PublicHolidays()
    expect:
    publicHolidays.isOnOrAfterNthBusinessDayOfMonth(LocalDate.parse(date), 4) == expected
    where:
    date         || expected
    "2026-02-04" || false
    "2026-02-05" || true
    "2026-02-06" || true
    "2026-02-28" || true
  }

}
