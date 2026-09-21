package ee.tuleva.onboarding.nudge

import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.deadline.PublicHolidays
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

import static ee.tuleva.onboarding.nudge.PaymentRateSeason.Mode.CLOSED
import static ee.tuleva.onboarding.nudge.PaymentRateSeason.Mode.LAST_DAYS
import static ee.tuleva.onboarding.nudge.PaymentRateSeason.Mode.OFF_SEASON
import static ee.tuleva.onboarding.nudge.PaymentRateSeason.Mode.SEASON

class PaymentRateSeasonsSpec extends Specification {

  static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn")

  @Unroll
  def "the payment rate season on #now is #mode"() {
    given:
    def clock = Clock.fixed(LocalDateTime.parse(now).atZone(TALLINN).toInstant(), TALLINN)
    def seasons = new PaymentRateSeasons(clock, new MandateDeadlinesService(clock, new PublicHolidays()))

    expect:
    seasons.current() == new PaymentRateSeason(LocalDate.parse(deadline), LocalDate.parse(fulfillmentDate), mode)

    where:
    now                    || deadline     | fulfillmentDate | mode
    "2026-09-01T00:00:00"  || "2026-11-30" | "2027-01-01"    | OFF_SEASON
    "2026-10-31T23:59:00"  || "2026-11-30" | "2027-01-01"    | OFF_SEASON
    "2026-11-01T00:00:00"  || "2026-11-30" | "2027-01-01"    | SEASON
    "2026-11-27T12:00:00"  || "2026-11-30" | "2027-01-01"    | SEASON
    "2026-11-28T00:00:00"  || "2026-11-30" | "2027-01-01"    | LAST_DAYS
    "2026-11-30T23:59:00"  || "2026-11-30" | "2027-01-01"    | LAST_DAYS
    "2026-12-01T00:00:00"  || "2027-11-30" | "2028-01-01"    | CLOSED
    "2027-01-15T12:00:00"  || "2027-11-30" | "2028-01-01"    | OFF_SEASON
    "2031-11-01T00:00:00"  || "2031-11-30" | "2032-01-01"    | SEASON
    "2031-11-28T09:00:00"  || "2031-11-30" | "2032-01-01"    | LAST_DAYS
    "2031-12-15T09:00:00"  || "2032-11-30" | "2033-01-01"    | CLOSED
  }

  @Unroll
  def "only a season in progress is shown to the saver: #mode"() {
    expect:
    new PaymentRateSeason(LocalDate.parse("2026-11-30"), LocalDate.parse("2027-01-01"), mode).isShown() == shown
    new PaymentRateSeason(LocalDate.parse("2026-11-30"), LocalDate.parse("2027-01-01"), mode).isClosed() == closed

    where:
    mode       || shown | closed
    OFF_SEASON || false | false
    SEASON     || true  | false
    LAST_DAYS  || true  | false
    CLOSED     || false | true
  }


  @Unroll
  def "the previous payment rate deadline on #now is #previousDeadline"() {
    given:
    def clock = Clock.fixed(LocalDateTime.parse(now).atZone(TALLINN).toInstant(), TALLINN)
    def seasons = new PaymentRateSeasons(clock, new MandateDeadlinesService(clock, new PublicHolidays()))

    expect:
    seasons.previousDeadline() == LocalDateTime.parse(previousDeadline).atZone(TALLINN).toInstant()

    where:
    now                   || previousDeadline
    "2026-09-15T09:00:00" || "2025-11-30T23:59:59.999999999"
    "2026-11-30T23:59:00" || "2025-11-30T23:59:59.999999999"
    "2026-12-01T00:00:00" || "2026-11-30T23:59:59.999999999"
  }
}
