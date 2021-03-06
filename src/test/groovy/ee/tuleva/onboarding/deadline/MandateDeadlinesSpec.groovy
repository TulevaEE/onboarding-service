package ee.tuleva.onboarding.deadline


import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import static ee.tuleva.onboarding.mandate.application.ApplicationType.*

class MandateDeadlinesSpec extends Specification {

  MandateDeadlines mandateDeadlines

  def "test getMandateDeadlines before march 31"() {
    given:
    def applicationDate = Instant.parse("2021-03-31T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock), applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2021-03-31T20:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2021-03-31T20:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2021-05-03") == getTransferMandateFulfillmentDate()

      Instant.parse("2021-07-31T20:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2021-09-01") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-03-31T20:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-04-16") == getWithdrawalFulfillmentDate()
    }
  }

  def "test getMandateDeadlines after march 31"() {
    given:
    def applicationDate = Instant.parse("2021-04-01T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock), applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2021-07-31T20:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2021-07-31T20:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2021-09-01") == getTransferMandateFulfillmentDate()

      Instant.parse("2021-11-30T21:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-01-03") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-04-30T20:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-05-17") == getWithdrawalFulfillmentDate()
    }
  }

  def "test getMandateDeadlines after 31 july"() {
    given:
    def applicationDate = Instant.parse("2021-08-11T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock), applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2021-11-30T21:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2021-11-30T21:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2022-01-03") == getTransferMandateFulfillmentDate()

      Instant.parse("2022-03-31T20:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-05-02") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-08-31T20:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-09-16") == getWithdrawalFulfillmentDate()
    }
  }

  def "can get fulfillment dates for different application types"() {
    given:
    def applicationDate = Instant.parse("2021-08-11T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock), applicationDate)

    then:
    with(mandateDeadlines) {
      getFulfillmentDate(TRANSFER) == getTransferMandateFulfillmentDate()
      getFulfillmentDate(EARLY_WITHDRAWAL) == getEarlyWithdrawalFulfillmentDate()
      getFulfillmentDate(WITHDRAWAL) == getWithdrawalFulfillmentDate()
    }
  }

  def "test previously done mandate deadlines after march 31"() {
    given:
    def applicationDate = Instant.parse("2021-03-31T10:00:00Z")
    def clock = Clock.fixed(Instant.parse("2021-04-01T10:00:00Z"), ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock), applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2021-03-31T20:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2021-03-31T20:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2021-05-03") == getTransferMandateFulfillmentDate()

      Instant.parse("2021-07-31T20:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2021-09-01") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-03-31T20:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-04-16") == getWithdrawalFulfillmentDate()
    }
  }

  def "can get cancellation deadlines for different application types"() {
    given:
    def applicationDate = Instant.parse("2021-08-11T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, new PublicHolidays(clock), applicationDate)

    then:
    with(mandateDeadlines) {
      getCancellationDeadline(TRANSFER) == getTransferMandateCancellationDeadline()
      getCancellationDeadline(EARLY_WITHDRAWAL) == getEarlyWithdrawalCancellationDeadline()
      getCancellationDeadline(WITHDRAWAL) == getWithdrawalCancellationDeadline()
    }
  }

}