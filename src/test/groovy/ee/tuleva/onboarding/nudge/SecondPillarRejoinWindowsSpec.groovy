package ee.tuleva.onboarding.nudge

import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.deadline.PublicHolidays
import spock.lang.Specification

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SecondPillarRejoinWindowsSpec extends Specification {

  static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn")

  def "the rejoin nudge on #now shows only in the month of the #deadline filing deadline: #open"() {
    given:
    def clock = Clock.fixed(LocalDateTime.parse(now).atZone(TALLINN).toInstant(), TALLINN)
    def windows = new SecondPillarRejoinWindows(clock, new MandateDeadlinesService(clock, new PublicHolidays()))

    expect:
    windows.current() == new SecondPillarRejoinWindow(LocalDate.parse(deadline), open)

    where:
    now                   || deadline     | open
    "2026-10-31T23:59:00" || "2026-11-30" | false
    "2026-11-01T00:00:00" || "2026-11-30" | true
    "2026-11-30T23:59:00" || "2026-11-30" | true
    "2026-12-01T00:00:00" || "2027-03-31" | false
    "2027-02-28T23:59:00" || "2027-03-31" | false
    "2027-03-01T00:00:00" || "2027-03-31" | true
    "2027-07-11T09:00:00" || "2027-07-31" | true
  }
}
