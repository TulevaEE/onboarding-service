package ee.tuleva.onboarding.savings.fund.nav

import ee.tuleva.onboarding.deadline.PublicHolidays
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

class NavCalendarSpec extends Specification {

  NavCalendar navCalendar = new NavCalendar(new PublicHolidays())

  @Unroll
  def "the NAV of #navDate is calculated on #calculationDate"() {
    expect:
    navCalendar.calculationDateOf(LocalDate.parse(navDate)) == LocalDate.parse(calculationDate)

    where:
    navDate      || calculationDate
    "2026-09-14" || "2026-09-15"
    "2026-09-11" || "2026-09-14"
    "2026-06-22" || "2026-06-25"
    "2026-12-23" || "2026-12-28"
    "2026-02-01" || "2026-02-02"
  }
}
