package ee.tuleva.onboarding.investment.instrument

import ee.tuleva.onboarding.instrument.InstrumentReferenceService
import ee.tuleva.onboarding.instrument.InstrumentRetirement
import ee.tuleva.onboarding.notification.OperationsNotificationService
import ee.tuleva.onboarding.time.MutableClock
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT

class InstrumentRetirementJobSpec extends Specification {

  InstrumentRetirementCandidateFinder retirementCandidateFinder = Stub()
  InstrumentRetirement instrumentRetirement = Mock()
  InstrumentReferenceService instrumentReferenceService = Stub()
  OperationsNotificationService notificationService = Mock()
  MutableClock clock = new MutableClock(Instant.parse("2026-09-24T07:15:00Z"))

  InstrumentRetirementJob job = new InstrumentRetirementJob(
      retirementCandidateFinder, instrumentRetirement, instrumentReferenceService,
      notificationService, clock)

  def setup() {
    instrumentReferenceService.refresh() >> true
  }

  def "retires an instrument that has been off the books for five nav dates and says so"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate()]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire("IE0009FT4LX4") >> true
    1 * notificationService.sendMessage(
        { it.contains("INSTRUMENT RETIRED") && it.contains("IE0009FT4LX4") &&
            it.contains("2026-08-27") && it.contains("5 NAV dates ago") },
        INVESTMENT)
  }

  def "says so when the retirement could not be applied to this instance's cache"() {
    given:
    def failingCacheService = Stub(InstrumentReferenceService) { refresh() >> false }
    def job = new InstrumentRetirementJob(
        retirementCandidateFinder, instrumentRetirement, failingCacheService, notificationService,
        clock)
    retirementCandidateFinder.findCandidates() >> [candidate()]
    instrumentRetirement.retire("IE0009FT4LX4") >> true

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * notificationService.sendMessage(
        { it.contains("INSTRUMENT RETIRED") && it.contains("could not be reloaded") }, INVESTMENT)
  }

  def "stays quiet when the instrument was already retired"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate()]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire("IE0009FT4LX4") >> false
    0 * notificationService.sendMessage(_ as String, INVESTMENT)
  }

  def "reports an instrument it could not retire instead of failing silently"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate()]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire("IE0009FT4LX4") >> { throw new IllegalStateException("still a benchmark proxy") }
    1 * notificationService.sendMessage(
        { it.contains("COULD NOT RETIRE") && it.contains("IE0009FT4LX4") }, INVESTMENT)
  }

  def "retires the instruments it can even when one of them fails"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate(), candidate("IE00BFG1TM61")]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire("IE0009FT4LX4") >> { throw new IllegalStateException("nope") }
    1 * instrumentRetirement.retire("IE00BFG1TM61") >> true
    1 * notificationService.sendMessage(
        { it.contains("INSTRUMENT RETIRED") && it.contains("IE00BFG1TM61") &&
            it.contains("COULD NOT RETIRE") && it.contains("IE0009FT4LX4") },
        INVESTMENT)
  }

  def "reports an unfixed failure once a day rather than on every hourly run"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate()]
    instrumentRetirement.retire("IE0009FT4LX4") >> { throw new IllegalStateException("still a benchmark proxy") }

    when:
    job.retireInstrumentsOffTheBooks()
    clock.tick(1, ChronoUnit.HOURS)
    job.retireInstrumentsOffTheBooks()
    clock.tick(1, ChronoUnit.HOURS)
    job.retireInstrumentsOffTheBooks()

    then:
    1 * notificationService.sendMessage({ it.contains("COULD NOT RETIRE") }, INVESTMENT)
  }

  def "reports the same failure again the next day"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate()]
    instrumentRetirement.retire("IE0009FT4LX4") >> { throw new IllegalStateException("still a benchmark proxy") }

    when:
    job.retireInstrumentsOffTheBooks()
    clock.tick(1, ChronoUnit.DAYS)
    job.retireInstrumentsOffTheBooks()

    then:
    2 * notificationService.sendMessage({ it.contains("COULD NOT RETIRE") }, INVESTMENT)
  }

  def "reports straight away when a new instrument starts failing the same day"() {
    given:
    retirementCandidateFinder.findCandidates() >>> [
        [candidate()],
        [candidate(), candidate("IE00BFG1TM61")]
    ]
    instrumentRetirement.retire(_ as String) >> { throw new IllegalStateException("nope") }

    when:
    job.retireInstrumentsOffTheBooks()
    clock.tick(1, ChronoUnit.HOURS)
    job.retireInstrumentsOffTheBooks()

    then:
    1 * notificationService.sendMessage({ !it.contains("IE00BFG1TM61") }, INVESTMENT)
    1 * notificationService.sendMessage({ it.contains("IE00BFG1TM61") }, INVESTMENT)
  }

  def "reports a failure that comes back after it was fixed the same day"() {
    given:
    retirementCandidateFinder.findCandidates() >>> [[candidate()], [], [candidate()]]
    instrumentRetirement.retire("IE0009FT4LX4") >> { throw new IllegalStateException("nope") }

    when:
    job.retireInstrumentsOffTheBooks()
    clock.tick(1, ChronoUnit.HOURS)
    job.retireInstrumentsOffTheBooks()
    clock.tick(1, ChronoUnit.HOURS)
    job.retireInstrumentsOffTheBooks()

    then:
    2 * notificationService.sendMessage({ it.contains("COULD NOT RETIRE") }, INVESTMENT)
  }

  def "still announces a retirement while an unchanged failure is being held back"() {
    given:
    retirementCandidateFinder.findCandidates() >>> [
        [candidate()],
        [candidate(), candidate("IE00BFG1TM61")]
    ]
    instrumentRetirement.retire("IE0009FT4LX4") >> { throw new IllegalStateException("nope") }
    instrumentRetirement.retire("IE00BFG1TM61") >> true

    when:
    job.retireInstrumentsOffTheBooks()
    clock.tick(1, ChronoUnit.HOURS)
    job.retireInstrumentsOffTheBooks()

    then:
    1 * notificationService.sendMessage({ !it.contains("INSTRUMENT RETIRED") }, INVESTMENT)
    1 * notificationService.sendMessage(
        { it.contains("INSTRUMENT RETIRED") && it.contains("IE00BFG1TM61") }, INVESTMENT)
  }

  private static InstrumentRetirementCandidateFinder.RetirementCandidate candidate(
      String isin = "IE0009FT4LX4") {
    new InstrumentRetirementCandidateFinder.RetirementCandidate(
        isin, "CCF Developed World Screened Index Fund", LocalDate.of(2026, 8, 27), 5L)
  }

}
