package ee.tuleva.onboarding.savings.fund

import ee.tuleva.onboarding.deadline.PublicHolidays
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest
import spock.lang.Specification
import java.time.*

class SavingFundDeadlinesServiceTest extends Specification {

  def publicHolidays = Mock(PublicHolidays)
  static def timeZone = ZoneId.of("Europe/Tallinn")
  def clock = Mock(Clock)

  def service = new SavingFundDeadlinesService(publicHolidays, clock)

  def setup() {
    clock.getZone() >> timeZone
    clock.instant() >> Instant.parse("2025-09-30T10:00:00Z")
  }

  def "getCancellationDeadline_shouldReturnCorrectDeadlineForSavingFundPayment"() {
    given:
    def payment = Mock(SavingFundPayment) {
      getReceivedBefore() >> (receivedStr ? Instant.parse(receivedStr) : null)
      getCreatedAt() >> Instant.parse(createdStr)
    }
    publicHolidays.nextWorkingDay(_ as LocalDate) >> { LocalDate date ->
      return date.plusDays(1)
    }

    expect:
    service.getCancellationDeadline(payment) == Instant.parse(expectedDeadline)

    where:
    receivedStr                    | createdStr                    | expectedDeadline
    "2025-09-30T12:00:00Z"         | "2025-09-30T09:00:00Z"        | "2025-09-30T13:00:00Z"
    "2025-09-30T16:00:00Z"         | "2025-09-30T09:00:00Z"        | "2025-10-01T13:00:00Z"
    null                           | "2025-09-30T09:00:00Z"        | "2025-09-30T13:00:00Z"
    null                           | "2025-09-30T13:00:00Z"        | "2025-10-01T13:00:00Z"
  }

  def "getFulfillmentDeadline_shouldReturnCorrectDeadlineForSavingFundPayment"() {
    given:
    def payment = Mock(SavingFundPayment) {
      getReceivedBefore() >> (receivedStr ? Instant.parse(receivedStr) : null)
      getCreatedAt() >> Instant.parse(createdStr ?: "2025-09-30T10:00:00Z")
    }
    publicHolidays.nextWorkingDay(_ as LocalDate) >> { LocalDate date ->
      return date.plusDays(1)
    }
    publicHolidays.addWorkingDays(_ as LocalDate, _) >> { LocalDate date, int days ->
      return date.plusDays(days)
    }

    expect:
    service.getFulfillmentDeadline(payment) == Instant.parse(expectedDeadline)

    where:
    receivedStr                    | createdStr                    | expectedDeadline
    "2025-09-29T12:00:00Z"         | "2025-09-29T10:00:00Z"        | "2025-09-30T13:00:00Z"
    "2025-09-30T12:00:00Z"         | "2025-09-30T10:00:00Z"        | "2025-10-01T13:00:00Z"
    null                           | "2025-09-30T13:00:00Z"        | "2025-10-02T13:00:00Z"
  }

  def "getCancellationDeadline_shouldReturnCorrectDeadlineForRedemptionRequest"() {
    given:
    def redemptionRequest = Mock(RedemptionRequest) {
      getRequestedAt() >> Instant.parse(requestedAtStr)
    }
    publicHolidays.nextWorkingDay(_ as LocalDate) >> { LocalDate date ->
      return date.plusDays(1)
    }

    expect:
    service.getCancellationDeadline(redemptionRequest) == Instant.parse(expectedDeadline)

    where:
    requestedAtStr                 | expectedDeadline
    "2025-09-30T09:00:00Z"         | "2025-09-30T13:00:00Z"
    "2025-09-30T13:00:00Z"         | "2025-10-01T13:00:00Z"
    "2025-09-29T12:00:00Z"         | "2025-09-29T13:00:00Z"
  }

  def "getFulfillmentDeadline_shouldReturnCorrectDeadlineForRedemptionRequest"() {
    given:
    def redemptionRequest = Mock(RedemptionRequest) {
      getRequestedAt() >> Instant.parse(requestedAtStr)
    }
    publicHolidays.nextWorkingDay(_ as LocalDate) >> { LocalDate date ->
      return date.plusDays(1)
    }
    publicHolidays.addWorkingDays(_ as LocalDate, _) >> { LocalDate date, int days ->
      return date.plusDays(days)
    }

    expect:
    service.getFulfillmentDeadline(redemptionRequest) == Instant.parse(expectedDeadline)

    where:
    requestedAtStr                 | expectedDeadline
    "2025-09-29T12:00:00Z"         | "2025-09-30T13:00:00Z"
    "2025-09-30T09:00:00Z"         | "2025-10-01T13:00:00Z"
    "2025-09-30T13:00:00Z"         | "2025-10-02T13:00:00Z"
  }
}
