package ee.tuleva.onboarding.deadline


import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

import static java.time.ZoneOffset.UTC

class MandateDeadlinesServiceSpec extends Specification {
  def time = Instant.parse("2021-03-11T10:00:00Z")
  Clock clock = Clock.fixed(time, UTC)
  PublicHolidays publicHolidays = new PublicHolidays()

  MandateDeadlinesService service = new MandateDeadlinesService(clock, publicHolidays)

  def "can get mandate deadlines"() {
    when:
    MandateDeadlines deadlines = service.getDeadlines()

    then:
    with(deadlines) {
      periodEnding == Instant.parse("2021-03-31T23:59:59.999999999Z")
      transferMandateCancellationDeadline == Instant.parse("2021-03-31T23:59:59.999999999Z")
      transferMandateFulfillmentDate == LocalDate.parse("2021-05-03")
      earlyWithdrawalCancellationDeadline == Instant.parse("2021-07-31T23:59:59.999999999Z")
      earlyWithdrawalFulfillmentDate == LocalDate.parse("2021-09-01")
      withdrawalCancellationDeadline == Instant.parse("2021-03-31T23:59:59.999999999Z")
      withdrawalFulfillmentDate == LocalDate.parse("2021-04-16")
    }
  }
}
