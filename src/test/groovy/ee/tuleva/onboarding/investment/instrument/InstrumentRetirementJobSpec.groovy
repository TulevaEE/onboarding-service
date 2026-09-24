package ee.tuleva.onboarding.investment.instrument

import ee.tuleva.onboarding.instrument.InstrumentReferenceService
import ee.tuleva.onboarding.instrument.InstrumentRetirement
import ee.tuleva.onboarding.notification.OperationsNotificationService
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT

class InstrumentRetirementJobSpec extends Specification {

  InstrumentRetirementCandidateFinder retirementCandidateFinder = Stub()
  InstrumentRetirement instrumentRetirement = Mock()
  InstrumentReferenceService instrumentReferenceService = Stub()
  OperationsNotificationService notificationService = Mock()

  InstrumentRetirementJob job = new InstrumentRetirementJob(
      retirementCandidateFinder, instrumentRetirement, instrumentReferenceService,
      notificationService)

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
        retirementCandidateFinder, instrumentRetirement, failingCacheService, notificationService)
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

  private static InstrumentRetirementCandidateFinder.RetirementCandidate candidate(
      String isin = "IE0009FT4LX4") {
    new InstrumentRetirementCandidateFinder.RetirementCandidate(
        isin, "CCF Developed World Screened Index Fund", LocalDate.of(2026, 8, 27), 5L)
  }

}
