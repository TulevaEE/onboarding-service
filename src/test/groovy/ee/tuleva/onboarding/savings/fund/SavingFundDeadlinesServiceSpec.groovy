package ee.tuleva.onboarding.savings.fund

import ee.tuleva.onboarding.deadline.PublicHolidays
import spock.lang.Specification
import java.time.*

import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture

class SavingFundDeadlinesServiceSpec extends Specification {

  def publicHolidays = new PublicHolidays()
  def clock = Clock.fixed(Instant.parse("2025-09-30T10:00:00Z"), ZoneId.of("Europe/Tallinn"))

  def service = new SavingFundDeadlinesService(publicHolidays, clock)

  def "getCancellationDeadline_returnsCorrectDeadlineForSavingFundPayment: #description"() {
    given:
    def payment = aPayment()
        .receivedBefore(receivedBefore ? Instant.parse(receivedBefore) : null)
        .createdAt(Instant.parse(createdAt))
        .build()

    expect:
    service.getCancellationDeadline(payment) == Instant.parse(expectedDeadline)

    where:
    description                                        | receivedBefore           | createdAt                | expectedDeadline
    "Mon 09:00 EEST -> today"                          | "2026-04-27T06:00:00Z"   | "2026-04-27T06:00:00Z"   | "2026-04-27T13:00:00Z"
    "Mon 16:00:00 EEST -> next day (cutoff edge)"      | "2026-04-27T13:00:00Z"   | "2026-04-27T13:00:00Z"   | "2026-04-28T13:00:00Z"
    "Mon 17:00 EEST -> next day"                       | "2026-04-27T14:00:00Z"   | "2026-04-27T14:00:00Z"   | "2026-04-28T13:00:00Z"
    "Fri 09:00 EEST -> today"                          | "2026-04-24T06:00:00Z"   | "2026-04-24T06:00:00Z"   | "2026-04-24T13:00:00Z"
    "Fri 16:00 EEST -> Mon (skip weekend)"             | "2026-04-24T13:00:00Z"   | "2026-04-24T13:00:00Z"   | "2026-04-27T13:00:00Z"
    "Sat 12:00 EEST -> Mon"                            | "2026-04-25T09:00:00Z"   | "2026-04-25T09:00:00Z"   | "2026-04-27T13:00:00Z"
    "Sat 16:00:00 EEST -> Mon (cutoff on non-working)" | "2026-04-25T13:00:00Z"   | "2026-04-25T13:00:00Z"   | "2026-04-27T13:00:00Z"
    "Sun 12:00 EEST -> Mon"                            | "2026-04-26T09:00:00Z"   | "2026-04-26T09:00:00Z"   | "2026-04-27T13:00:00Z"
    "Sun 23:59 EEST -> Mon (incident)"                 | "2026-04-26T20:59:59Z"   | "2026-04-27T01:01:26Z"   | "2026-04-27T13:00:00Z"
    "Tue Independence Day -> Wed (EET)"                | "2026-02-24T09:00:00Z"   | "2026-02-24T09:00:00Z"   | "2026-02-25T14:00:00Z"
    "Wed Christmas Eve -> Mon Dec 29 (EET)"            | "2025-12-24T10:00:00Z"   | "2025-12-24T10:00:00Z"   | "2025-12-29T14:00:00Z"
    "Thu Christmas Day -> Mon Dec 29 (EET)"            | "2025-12-25T10:00:00Z"   | "2025-12-25T10:00:00Z"   | "2025-12-29T14:00:00Z"
    "Tue Dec 23 17:00 -> Mon Dec 29 (EET)"             | "2025-12-23T15:00:00Z"   | "2025-12-23T15:00:00Z"   | "2025-12-29T14:00:00Z"
    "Easter Sunday -> Mon (holiday-on-Sun)"            | "2026-04-05T10:00:00Z"   | "2026-04-05T10:00:00Z"   | "2026-04-06T13:00:00Z"
    "DST forward Sun -> Mon"                           | "2026-03-29T10:00:00Z"   | "2026-03-29T10:00:00Z"   | "2026-03-30T13:00:00Z"
    "fallback to createdAt when receivedBefore null"   | null                     | "2026-04-26T09:00:00Z"   | "2026-04-27T13:00:00Z"
  }

  def "getFulfillmentDeadline_returnsCorrectDeadlineForSavingFundPayment: #description"() {
    given:
    def payment = aPayment()
        .receivedBefore(Instant.parse(receivedBefore))
        .build()

    expect:
    service.getFulfillmentDeadline(payment) == Instant.parse(expectedDeadline)

    where:
    description                                 | receivedBefore           | expectedDeadline
    "Sun 12:00 EEST -> Tue 16:00"               | "2026-04-26T09:00:00Z"   | "2026-04-28T13:00:00Z"
    "Wed Christmas Eve -> Tue Dec 30 (EET)"     | "2025-12-24T10:00:00Z"   | "2025-12-30T14:00:00Z"
  }

  def "getCancellationDeadline_returnsCorrectDeadlineForRedemptionRequest: #description"() {
    given:
    def redemptionRequest = redemptionRequestFixture()
        .requestedAt(Instant.parse(requestedAt))
        .build()

    expect:
    service.getCancellationDeadline(redemptionRequest) == Instant.parse(expectedDeadline)

    where:
    description                            | requestedAt              | expectedDeadline
    "Mon 17:00 EEST -> next day"           | "2026-04-27T14:00:00Z"   | "2026-04-28T13:00:00Z"
    "Sun 12:00 EEST -> Mon"                | "2026-04-26T09:00:00Z"   | "2026-04-27T13:00:00Z"
    "Tue Dec 23 17:00 -> Mon Dec 29 (EET)" | "2025-12-23T15:00:00Z"   | "2025-12-29T14:00:00Z"
  }

  def "getFulfillmentDeadline_returnsCorrectDeadlineForRedemptionRequest: #description"() {
    given:
    def redemptionRequest = redemptionRequestFixture()
        .requestedAt(Instant.parse(requestedAt))
        .build()

    expect:
    service.getFulfillmentDeadline(redemptionRequest) == Instant.parse(expectedDeadline)

    where:
    description                             | requestedAt              | expectedDeadline
    "Sun 12:00 EEST -> Tue 16:00"           | "2026-04-26T09:00:00Z"   | "2026-04-28T13:00:00Z"
    "Wed Christmas Eve -> Tue Dec 30 (EET)" | "2025-12-24T10:00:00Z"   | "2025-12-30T14:00:00Z"
  }
}
