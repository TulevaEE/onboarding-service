package ee.tuleva.onboarding.deadline

import spock.lang.Specification

import java.time.LocalDate
import java.time.Month

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

}
