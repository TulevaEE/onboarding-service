package ee.tuleva.onboarding.investment.epis

import ee.tuleva.onboarding.investment.calendar.Target2Calendar
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.*

class PevaRavaPeriodServiceSpec extends Specification {

  PevaRavaPeriodService service = new PevaRavaPeriodService(new Target2Calendar())

  def "returns three cycles per year with exec dates adjusted to next TARGET2 business day"() {
    when:
    def periods = service.executionPeriods(2026)

    then:
    periods == [
      new PevaRavaCycle(LocalDate.of(2025, 11, 30), LocalDate.of(2026, 1, 2)),
      new PevaRavaCycle(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 4)),
      new PevaRavaCycle(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 9, 1)),
    ]
  }

  @Unroll
  def "phase on #today is #phase"() {
    expect:
    service.getCurrentPhase(LocalDate.parse(today)) == phase

    where:
    today        | phase
    "2026-07-30" | DONE
    "2026-07-31" | DATA_VALID
    "2026-08-12" | DATA_VALID
    "2026-08-13" | TUK00_ACTIVE
    "2026-08-20" | TUK00_ACTIVE
    "2026-08-21" | ACTIVE
    "2026-09-03" | ACTIVE
    "2026-09-04" | DONE
    "2026-11-29" | DONE
    "2026-11-30" | DATA_VALID
    "2025-12-15" | TUK00_ACTIVE
  }

  def "september cycle timelines use TARGET2 business day arithmetic"() {
    when:
    def period = service.getCurrentPeriod(LocalDate.of(2026, 8, 24)).orElseThrow()

    then:
    period.cycle() == new PevaRavaCycle(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 9, 1))
    period.timelineFor(TUK75) == new FundCycleTimeline(
      LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 26), true, false)
    period.timelineFor(TUK00) == new FundCycleTimeline(
      LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 24), true, true)
  }

  def "current period covers from lock date until next cycle lock date"() {
    when:
    def period = service.getCurrentPeriod(LocalDate.of(2026, 11, 29)).orElseThrow()

    then:
    period.phase() == DONE
    period.cycle() == new PevaRavaCycle(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 9, 1))
  }
}
