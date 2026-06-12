package ee.tuleva.onboarding.investment.calendar

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

class Target2CalendarSpec extends Specification {

  Target2Calendar calendar = new Target2Calendar()

  @Unroll
  def "isBusinessDay(#date) == #expected"() {
    expect:
    calendar.isBusinessDay(LocalDate.parse(date)) == expected

    where:
    date         | expected
    "2026-01-01" | false // New Year's Day
    "2025-04-18" | false // Good Friday 2025
    "2025-04-21" | false // Easter Monday 2025
    "2026-04-03" | false // Good Friday 2026
    "2026-04-06" | false // Easter Monday 2026
    "2026-05-01" | false // Labour Day
    "2025-12-25" | false // Christmas Day
    "2025-12-26" | false // Christmas Holiday
    "2026-01-10" | false // Saturday
    "2026-01-11" | false // Sunday
    "2026-01-12" | true
    "2025-04-17" | true // Maundy Thursday is open
    "2026-03-17" | true // St Patrick's Day is open in TARGET2
    "2026-07-14" | true // Bastille Day is open in TARGET2
    "2026-06-23" | true // Luxembourg National Day is open in TARGET2
  }

  @Unroll
  def "addBusinessDays(#date, #days) == #expected"() {
    expect:
    calendar.addBusinessDays(LocalDate.parse(date), days) == LocalDate.parse(expected)

    where:
    date         | days | expected
    "2025-04-17" | 1    | "2025-04-22" // Thu over Good Friday, weekend, Easter Monday
    "2025-04-17" | 2    | "2025-04-23"
    "2026-04-01" | 2    | "2026-04-07" // Wed over Good Friday 2026-04-03, Easter Monday 2026-04-06
    "2026-01-12" | 2    | "2026-01-14"
    "2026-01-09" | 2    | "2026-01-13" // Fri over weekend
    "2026-04-30" | 1    | "2026-05-04" // Thu over Labour Day and weekend
    "2026-01-12" | 0    | "2026-01-12"
  }

  @Unroll
  def "subtractBusinessDays(#date, #days) == #expected"() {
    expect:
    calendar.subtractBusinessDays(LocalDate.parse(date), days) == LocalDate.parse(expected)

    where:
    date         | days | expected
    "2025-04-23" | 2    | "2025-04-17"
    "2026-04-07" | 2    | "2026-04-01"
    "2026-01-14" | 2    | "2026-01-12"
    "2026-05-04" | 1    | "2026-04-30"
    "2026-01-12" | 0    | "2026-01-12"
  }
}
