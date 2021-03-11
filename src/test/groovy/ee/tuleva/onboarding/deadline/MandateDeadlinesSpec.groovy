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
      LocalDate.parse("2021-03-31") == getPeriodEnding()

      LocalDate.parse("2021-03-31") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2021-05-03") == getTransferMandateFulfillmentDate()

      LocalDate.parse("2021-07-31") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2021-09-01") == getEarlyWithdrawalFulfillmentDate()

      LocalDate.parse("2021-03-31") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-04-16") == getWithdrawalFulfillmentDate()
    }
  }

  def "test getMandateDeadlines after march 31"() {
    given:
    Clock clock = Clock.fixed(Instant.parse("2021-04-01T10:00:00Z"), UTC)

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock))

    then:
    with(mandateDeadlines) {
      LocalDate.parse("2021-07-31") == getPeriodEnding()

      LocalDate.parse("2021-07-31") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2021-09-01") == getTransferMandateFulfillmentDate()

      LocalDate.parse("2021-11-30") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-01-03") == getEarlyWithdrawalFulfillmentDate()

      LocalDate.parse("2021-04-30") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-05-17") == getWithdrawalFulfillmentDate()
    }
  }

  def "test getMandateDeadlines after 31 july"() {
    given:
    Clock clock = Clock.fixed(Instant.parse("2021-08-11T10:00:00Z"), UTC)

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock))

    then:
    with(mandateDeadlines) {
      LocalDate.parse("2021-11-30") == getPeriodEnding()

      LocalDate.parse("2021-11-30") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2022-01-03") == getTransferMandateFulfillmentDate()

      LocalDate.parse("2022-03-31") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-05-02") == getEarlyWithdrawalFulfillmentDate()

      LocalDate.parse("2021-08-31") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-09-16") == getWithdrawalFulfillmentDate()
    }
  }
}