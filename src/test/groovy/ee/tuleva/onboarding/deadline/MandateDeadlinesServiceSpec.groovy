package ee.tuleva.onboarding.deadline


import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

import static java.time.ZoneOffset.UTC

class MandateDeadlinesServiceSpec extends Specification {
  Clock clock = Clock.fixed(Instant.parse("2021-03-11T10:00:00Z"), UTC)
  PublicHolidays publicHolidays = new PublicHolidays(clock)

  MandateDeadlinesService service = new MandateDeadlinesService(clock, publicHolidays)

  def "can get mandate deadlines"() {
    when:
    MandateDeadlines deadlines = service.getDeadlines()
    then:
    deadlines.periodEnding == LocalDate.parse("2021-03-31")
    deadlines.transferMandateCancellationDeadline == LocalDate.parse("2021-03-31")
    deadlines.transferMandateFulfillmentDate == LocalDate.parse("2021-05-03")
    deadlines.earlyWithdrawalCancellationDeadline == LocalDate.parse("2021-07-31")
    deadlines.earlyWithdrawalFulfillmentDate == LocalDate.parse("2021-09-01")
    deadlines.withdrawalCancellationDeadline == LocalDate.parse("2021-03-31")
    deadlines.withdrawalFulfillmentDate == LocalDate.parse("2021-04-16")
  }
}
