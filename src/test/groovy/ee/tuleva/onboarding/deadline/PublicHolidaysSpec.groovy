package ee.tuleva.onboarding.deadline

import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month

import static java.time.ZoneOffset.UTC

class PublicHolidaysSpec extends Specification {

  static final Clock clock = Clock.fixed(Instant.parse("2021-03-11T10:00:00Z"), UTC)


  def "can get estonian public holidays"() {
    when:
    PublicHolidays publicHolidays = new PublicHolidays(clock)

    then:
    with(publicHolidays) {
      LocalDate.parse("2021-01-01") == newYearsDay()
      LocalDate.parse("2021-02-24") == independenceDay()
      LocalDate.parse("2021-04-02") == goodFriday()
      LocalDate.parse("2021-04-04") == easterSunday()
      LocalDate.parse("2021-05-01") == springDay()
      LocalDate.parse("2021-05-23") == pentecost()
      LocalDate.parse("2021-06-23") == victoryDay()
      LocalDate.parse("2021-06-24") == midsummerDay()
      LocalDate.parse("2021-08-20") == dayOfRestorationOfIndependence()
      LocalDate.parse("2021-12-24") == christmasEve()
      LocalDate.parse("2021-12-25") == christmasDay()
      LocalDate.parse("2021-12-26") == boxingDay()
    }
  }

  def "christmas day is not working day"() {
    when:
    PublicHolidays publicHolidays = new PublicHolidays(clock)
    then:
    !publicHolidays.isWorkingDay(publicHolidays.christmasDay())
  }

  def "next workday from christmas day 2021 is 27th"() {
    when:
    PublicHolidays publicHolidays = new PublicHolidays(clock)
    then:
    publicHolidays.nextWorkingDay(publicHolidays.christmasDay()) ==
        LocalDate.of(2021, Month.DECEMBER, 27)
  }

  def "three working days after christmas day, christmas eve or boxing day 2021 is wednesday"() {
    given:
    LocalDate threeWorkingDaysAfterChristmas = LocalDate.of(2021, Month.DECEMBER, 29)
    when:
    PublicHolidays publicHolidays = new PublicHolidays(clock)
    then:
    publicHolidays.addWorkingDays(publicHolidays.christmasDay(), 3) == threeWorkingDaysAfterChristmas
    publicHolidays.addWorkingDays(publicHolidays.boxingDay(), 3) == threeWorkingDaysAfterChristmas
    publicHolidays.addWorkingDays(publicHolidays.christmasEve(), 3) == threeWorkingDaysAfterChristmas
  }

}
