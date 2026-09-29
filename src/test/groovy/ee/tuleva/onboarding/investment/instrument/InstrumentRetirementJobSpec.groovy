package ee.tuleva.onboarding.investment.instrument

import ee.tuleva.onboarding.instrument.InstrumentRetirement
import ee.tuleva.onboarding.instrument.InstrumentRetirementOutcome
import ee.tuleva.onboarding.instrument.InstrumentRetirementOutcome.Refusal
import ee.tuleva.onboarding.investment.instrument.InstrumentRetirementCandidateFinder.RetirementCandidate
import ee.tuleva.onboarding.notification.OperationsNotificationService
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO

class InstrumentRetirementJobSpec extends Specification {

  static final String EXITED_ISIN = "IE0009FT4LX4"
  static final String OTHER_EXITED_ISIN = "IE00BFG1TM61"

  InstrumentRetirementCandidateFinder retirementCandidateFinder = Stub()
  InstrumentRetirement instrumentRetirement = Mock()
  OperationsNotificationService notificationService = Mock()

  InstrumentRetirementJob job = new InstrumentRetirementJob(
      retirementCandidateFinder, instrumentRetirement, notificationService)

  def "retires an instrument neither held nor modelled for five nav dates and says this instance reloaded"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate(EXITED_ISIN)]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire([EXITED_ISIN]) >> outcome([EXITED_ISIN], [], true)
    1 * notificationService.sendMessage(
        "INSTRUMENT RETIRED — neither held nor in a model for long enough, so active is now false\n" +
            "  IE0009FT4LX4 CCF Developed World Screened Index Fund — neither held nor in a model" +
            " after 2026-08-27, 5 NAV dates since\n" +
            "This instance reloaded its instrument cache; the others keep importing and checking" +
            " these prices until their next hourly reload. Stored prices and findByIsin are" +
            " unaffected.",
        INVESTMENT, INFO)
    0 * notificationService._
  }

  def "says every instance waits for its hourly reload when this instance could not reload its cache"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate(EXITED_ISIN)]
    instrumentRetirement.retire([EXITED_ISIN]) >> outcome([EXITED_ISIN], [], false)

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * notificationService.sendMessage(
        "INSTRUMENT RETIRED — neither held nor in a model for long enough, so active is now false\n" +
            "  IE0009FT4LX4 CCF Developed World Screened Index Fund — neither held nor in a model" +
            " after 2026-08-27, 5 NAV dates since\n" +
            "This instance could not reload its instrument cache, so every instance keeps importing" +
            " and checking these prices until its next hourly reload. Stored prices and" +
            " findByIsin are unaffected.",
        INVESTMENT, INFO)
    0 * notificationService._
  }

  def "stays quiet when the instrument was already retired"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate(EXITED_ISIN)]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire([EXITED_ISIN]) >> outcome([], [], false)
    0 * notificationService._
  }

  def "reports an instrument it could not retire at error instead of failing silently"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate(EXITED_ISIN)]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire([EXITED_ISIN]) >>
        outcome([], [new Refusal(EXITED_ISIN, "still a benchmark proxy")], false)
    1 * notificationService.sendMessage(
        "COULD NOT RETIRE — fix the instrument reference data; the job tries again the next" +
            " working day\n" +
            "  IE0009FT4LX4 — still a benchmark proxy",
        INVESTMENT, ERROR)
    0 * notificationService._
  }

  def "reports the instruments it retired and the ones it could not in one error message"() {
    given:
    retirementCandidateFinder.findCandidates() >> [candidate(EXITED_ISIN), candidate(OTHER_EXITED_ISIN)]

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    1 * instrumentRetirement.retire([EXITED_ISIN, OTHER_EXITED_ISIN]) >>
        outcome([OTHER_EXITED_ISIN], [new Refusal(EXITED_ISIN, "still a benchmark proxy")], true)
    1 * notificationService.sendMessage(
        "INSTRUMENT RETIRED — neither held nor in a model for long enough, so active is now false\n" +
            "  IE00BFG1TM61 CCF Developed World Screened Index Fund — neither held nor in a model" +
            " after 2026-08-27, 5 NAV dates since\n" +
            "This instance reloaded its instrument cache; the others keep importing and checking" +
            " these prices until their next hourly reload. Stored prices and findByIsin are" +
            " unaffected.\n" +
            "COULD NOT RETIRE — fix the instrument reference data; the job tries again the next" +
            " working day\n" +
            "  IE0009FT4LX4 — still a benchmark proxy",
        INVESTMENT, ERROR)
    0 * notificationService._
  }

  def "says the retirement check could not run when finding the candidates fails"() {
    given:
    retirementCandidateFinder.findCandidates() >> { throw new IllegalStateException("model data changed mid-run") }

    when:
    job.retireInstrumentsOffTheBooks()

    then:
    0 * instrumentRetirement._
    1 * notificationService.sendMessage(
        "INSTRUMENT RETIREMENT CHECK COULD NOT RUN — nothing was retired today; the cause is in" +
            " the application log. The job tries again the next working day.",
        INVESTMENT, ERROR)
    0 * notificationService._
  }

  private static InstrumentRetirementOutcome outcome(
      List<String> retiredIsins, List<Refusal> refusals, boolean cacheReloadedOnThisInstance) {
    new InstrumentRetirementOutcome(retiredIsins, refusals, cacheReloadedOnThisInstance)
  }

  private static RetirementCandidate candidate(String isin) {
    new RetirementCandidate(
        isin, "CCF Developed World Screened Index Fund", LocalDate.of(2026, 8, 27), 5L)
  }
}
