package ee.tuleva.onboarding.investment.instrument

import ee.tuleva.onboarding.instrument.InstrumentRetirement
import ee.tuleva.onboarding.instrument.InstrumentRetirementOutcome
import ee.tuleva.onboarding.instrument.InstrumentRetirementOutcome.Refusal
import ee.tuleva.onboarding.notification.OperationsNotificationService
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT

class InstrumentRetirementJobSpec extends Specification {

  InstrumentRetirementCandidateFinder retirementCandidateFinder = Stub()
  InstrumentRetirement instrumentRetirement = Mock()
  OperationsNotificationService notificationService = Mock()

  InstrumentRetirementJob job = new InstrumentRetirementJob(
      retirementCandidateFinder, instrumentRetirement, notificationService)

  def "retires an instrument that has been off the books for five nav dates and says so"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate()]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire(["IE0009FT4LX4"]) >> outcome(["IE0009FT4LX4"], [], true)
    1 * notificationService.sendMessage(
        { it.contains("INSTRUMENT RETIRED") && it.contains("IE0009FT4LX4") &&
            it.contains("2026-08-27") && it.contains("5 NAV dates ago") },
        INVESTMENT)
  }

  def "says so when the retirement could not be applied to this instance's cache"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate()]
    instrumentRetirement.retire(["IE0009FT4LX4"]) >> outcome(["IE0009FT4LX4"], [], false)

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
    1 * instrumentRetirement.retire(["IE0009FT4LX4"]) >> outcome([], [], false)
    0 * notificationService.sendMessage(_ as String, INVESTMENT)
  }

  def "reports an instrument it could not retire instead of failing silently"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate()]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire(["IE0009FT4LX4"]) >>
        outcome([], [new Refusal("IE0009FT4LX4", "still a benchmark proxy")], false)
    1 * notificationService.sendMessage(
        { it.contains("COULD NOT RETIRE") && it.contains("IE0009FT4LX4") }, INVESTMENT)
  }

  def "retires the instruments it can even when one of them fails"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate(), candidate("IE00BFG1TM61")]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire(["IE0009FT4LX4", "IE00BFG1TM61"]) >>
        outcome(["IE00BFG1TM61"], [new Refusal("IE0009FT4LX4", "nope")], true)
    1 * notificationService.sendMessage(
        { it.contains("INSTRUMENT RETIRED") && it.contains("IE00BFG1TM61") &&
            it.contains("COULD NOT RETIRE") && it.contains("IE0009FT4LX4") },
        INVESTMENT)
  }

  private static InstrumentRetirementOutcome outcome(
      List<String> retiredIsins, List<Refusal> refusals, boolean cacheReloadedOnThisInstance) {
    new InstrumentRetirementOutcome(retiredIsins, refusals, cacheReloadedOnThisInstance)
  }

  private static InstrumentRetirementCandidateFinder.RetirementCandidate candidate(
      String isin = "IE0009FT4LX4") {
    new InstrumentRetirementCandidateFinder.RetirementCandidate(
        isin, "CCF Developed World Screened Index Fund", LocalDate.of(2026, 8, 27), 5L)
  }

}
