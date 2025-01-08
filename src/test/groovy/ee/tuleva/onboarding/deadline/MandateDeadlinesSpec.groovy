package ee.tuleva.onboarding.deadline

import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import static ee.tuleva.onboarding.mandate.application.ApplicationType.*

class MandateDeadlinesSpec extends Specification {

  MandateDeadlines mandateDeadlines
  PublicHolidays publicHolidays = new PublicHolidays()

  def "test getMandateDeadlines before march 31"() {
    given:
    def applicationDate = Instant.parse("2021-03-31T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2021-03-31T20:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2021-03-31T20:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2021-05-03") == getTransferMandateFulfillmentDate()

      Instant.parse("2021-07-31T20:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2021-09-01") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-03-31T20:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-04-16") == getWithdrawalFulfillmentDate()
      LocalDate.parse("2021-04-20") == getWithdrawalLatestFulfillmentDate()
      LocalDate.parse("2021-09-01") == getSecondPillarContributionEndDate()
    }
  }

  def "test getMandateDeadlines after march 31"() {
    given:
    def applicationDate = Instant.parse("2021-04-01T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2021-07-31T20:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2021-07-31T20:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2021-09-01") == getTransferMandateFulfillmentDate()

      Instant.parse("2021-11-30T21:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-01-03") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-04-30T20:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-05-16") == getWithdrawalFulfillmentDate()
      LocalDate.parse("2021-05-20") == getWithdrawalLatestFulfillmentDate()
      LocalDate.parse("2022-01-01") == getSecondPillarContributionEndDate()
    }
  }

  def "test getMandateDeadlines after 31 july"() {
    given:
    def applicationDate = Instant.parse("2021-08-11T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2021-11-30T21:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2021-11-30T21:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2022-01-03") == getTransferMandateFulfillmentDate()

      Instant.parse("2022-03-31T20:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-05-02") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-08-31T20:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-09-16") == getWithdrawalFulfillmentDate()
      LocalDate.parse("2021-09-20") == getWithdrawalLatestFulfillmentDate()
      LocalDate.parse("2022-05-01") == getSecondPillarContributionEndDate()
    }
  }

  def "test getMandateDeadlines after 30 november"() {
    given:
    def applicationDate = Instant.parse("2021-12-01T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2022-03-31T20:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2022-03-31T20:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2022-05-02") == getTransferMandateFulfillmentDate()

      Instant.parse("2022-07-31T20:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-09-01") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-12-31T21:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2022-01-16") == getWithdrawalFulfillmentDate()
      LocalDate.parse("2022-01-20") == getWithdrawalLatestFulfillmentDate()
      LocalDate.parse("2022-09-01") == getSecondPillarContributionEndDate()
    }
  }

  def "can get fulfillment dates for different application types"() {
    given:
    def applicationDate = Instant.parse("2021-08-11T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
    with(mandateDeadlines) {
      getFulfillmentDate(TRANSFER) == getTransferMandateFulfillmentDate()
      getFulfillmentDate(EARLY_WITHDRAWAL) == getEarlyWithdrawalFulfillmentDate()
      getFulfillmentDate(WITHDRAWAL) == getWithdrawalLatestFulfillmentDate()
    }
  }

  def "test previously done mandate deadlines after march 31"() {
    given:
    def applicationDate = Instant.parse("2021-03-31T10:00:00Z")
    def clock = Clock.fixed(Instant.parse("2021-04-01T10:00:00Z"), ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2021-03-31T20:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2021-03-31T20:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2021-05-03") == getTransferMandateFulfillmentDate()

      Instant.parse("2021-07-31T20:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2021-09-01") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-03-31T20:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-04-16") == getWithdrawalFulfillmentDate()
      LocalDate.parse("2021-04-20") == getWithdrawalLatestFulfillmentDate()
    }
  }

  def "test mandate deadlines after year change"() {
    given:
    def applicationDate = Instant.parse("2021-10-10T10:00:00Z")
    def clock = Clock.fixed(Instant.parse("2022-01-05T10:00:00Z"), ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2021-11-30T21:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2021-11-30T21:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2022-01-03") == getTransferMandateFulfillmentDate()

      Instant.parse("2022-03-31T20:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2022-05-02") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2021-10-31T21:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2021-11-16") == getWithdrawalFulfillmentDate()
      LocalDate.parse("2021-11-20") == getWithdrawalLatestFulfillmentDate()
    }
  }

  def "test mandate deadlines work correctly across year boundaries and next year public holidays"() {
    given:
    def applicationDate = Instant.parse("2023-11-30T10:00:00Z")
    def clock = Clock.fixed(Instant.parse("2023-11-30T10:00:00Z"), ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
    with(mandateDeadlines) {
      Instant.parse("2023-11-30T21:59:59.999999999Z") == getPeriodEnding()

      Instant.parse("2023-11-30T21:59:59.999999999Z") == getTransferMandateCancellationDeadline()
      LocalDate.parse("2024-01-02") == getTransferMandateFulfillmentDate()

      Instant.parse("2024-03-31T20:59:59.999999999Z") == getEarlyWithdrawalCancellationDeadline()
      LocalDate.parse("2024-05-02") == getEarlyWithdrawalFulfillmentDate()

      Instant.parse("2023-11-30T21:59:59.999999999Z") == getWithdrawalCancellationDeadline()
      LocalDate.parse("2023-12-16") == getWithdrawalFulfillmentDate()
      LocalDate.parse("2023-12-20") == getWithdrawalLatestFulfillmentDate()
    }
  }

  def "can get cancellation deadlines for different application types"() {
    given:
    def applicationDate = Instant.parse("2021-08-11T10:00:00Z")
    def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
    mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
    with(mandateDeadlines) {
      getCancellationDeadline(TRANSFER) == getTransferMandateCancellationDeadline()
      getCancellationDeadline(EARLY_WITHDRAWAL) == getEarlyWithdrawalCancellationDeadline()
      getCancellationDeadline(WITHDRAWAL) == getWithdrawalCancellationDeadline()
      getCancellationDeadline(WITHDRAWAL_THIRD_PILLAR) == getNonCancellableApplicationDeadline()
    }
  }

  def "test getPaymentRateDeadline before November 30 (2024-11-29) responds 2024-11-30"() {
    given:
        def applicationDate = Instant.parse("2024-11-29T10:00:00Z")
        def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
        mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
        mandateDeadlines.getPaymentRateDeadline() == Instant.parse("2024-11-30T21:59:59.999999999Z")
  }

  def "test getPaymentRateDeadline after November 30(2024-12-01) responds 2025-11-30"() {
    given:
        def applicationDate = Instant.parse("2024-12-01T10:00:00Z")
        def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
        mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
        mandateDeadlines.getPaymentRateDeadline() == Instant.parse("2025-11-30T21:59:59.999999999Z")
  }

  def "test getPaymentRateFulfillmentDate before November 30(2024-11-29) responds 2025-01-01"() {
    given:
        def applicationDate = Instant.parse("2024-11-29T10:00:00Z")
        def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
        mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
        mandateDeadlines.getPaymentRateFulfillmentDate() == LocalDate.parse("2025-01-01")
  }

  def "test getPaymentRateFulfillmentDate after November 30(2024-12-01) responds 2026-01-01"() {
    given:
        def applicationDate = Instant.parse("2024-12-01T10:00:00Z")
        def clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"))

    when:
        mandateDeadlines = new MandateDeadlines(clock, publicHolidays, applicationDate)

    then:
        mandateDeadlines.getPaymentRateFulfillmentDate() == LocalDate.parse("2026-01-01")
  }

}
