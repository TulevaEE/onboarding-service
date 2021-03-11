package ee.tuleva.onboarding.deadline

import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

import static java.time.ZoneOffset.UTC

class PublicHolidaysSpec extends Specification {

  def "can get estonian public holidays"() {
    given:
    Clock clock = Clock.fixed(Instant.parse("2021-03-11T10:00:00Z"), UTC)

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

}
