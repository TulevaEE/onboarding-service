package ee.tuleva.onboarding.deadline

import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

import static java.time.ZoneOffset.UTC

class MandateDeadlinesSpec extends Specification {

  MandateDeadlines mandateDeadlines

  def "test getMandateDeadlines before march 31"() {
    given:
    Clock clock = Clock.fixed(Instant.parse("2021-03-31T10:00:00Z"), UTC)

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock))

    then:
    with(mandateDeadlines) {
      LocalDate.parse("2021-03-31") == periodEnding()

      LocalDate.parse("2021-03-31") == transferMandateCancellationDeadline()
      LocalDate.parse("2021-05-03") == transferMandateFulfillmentDate()

      LocalDate.parse("2021-07-31") == earlyWithdrawalCancellationDeadline()
      LocalDate.parse("2021-09-01") == earlyWithdrawalFulfillmentDate()

      LocalDate.parse("2021-03-31") == withdrawalCancellationDeadline()
      LocalDate.parse("2021-04-16") == withdrawalFulfillmentDate()
    }
  }

  def "test getMandateDeadlines after march 31"() {
    given:
    Clock clock = Clock.fixed(Instant.parse("2021-04-01T10:00:00Z"), UTC)

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock))

    then:
    with(mandateDeadlines) {
      LocalDate.parse("2021-07-31") == periodEnding()

      LocalDate.parse("2021-07-31") == transferMandateCancellationDeadline()
      LocalDate.parse("2021-09-01") == transferMandateFulfillmentDate()

      LocalDate.parse("2021-11-30") == earlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-01-03") == earlyWithdrawalFulfillmentDate()

      LocalDate.parse("2021-04-30") == withdrawalCancellationDeadline()
      LocalDate.parse("2021-05-17") == withdrawalFulfillmentDate()
    }
  }

  def "test getMandateDeadlines after 31 july"() {
    given:
    Clock clock = Clock.fixed(Instant.parse("2021-08-11T10:00:00Z"), UTC)

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock))

    then:
    with(mandateDeadlines) {
      LocalDate.parse("2021-11-30") == periodEnding()

      LocalDate.parse("2021-11-30") == transferMandateCancellationDeadline()
      LocalDate.parse("2022-01-03") == transferMandateFulfillmentDate()

      LocalDate.parse("2022-03-31") == earlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-05-02") == earlyWithdrawalFulfillmentDate()

      LocalDate.parse("2021-08-31") == withdrawalCancellationDeadline()
      LocalDate.parse("2021-09-16") == withdrawalFulfillmentDate()
    }
  }
}