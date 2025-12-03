package ee.tuleva.onboarding.savings.fund

import ee.tuleva.onboarding.deadline.PublicHolidays
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

  def "getCancellationDeadline returns correct deadline"() {
    given:
    def payment = Mock(SavingFundPayment) {
      getReceivedBefore() >> (receivedStr ? Instant.parse(receivedStr) : null)
    }
    publicHolidays.nextWorkingDay(_ as LocalDate) >> { LocalDate date ->
      return date.plusDays(1)
    }

    expect:
    service.getCancellationDeadline(payment) == Instant.parse(expectedDeadline)

    where:
    receivedStr                    | expectedDeadline
    "2025-09-30T12:00:00Z"         | "2025-09-30T13:00:00Z" // before cutoff
    "2025-09-30T16:00:00Z"         | "2025-10-01T13:00:00Z" // at cutoff
    null                           | "2025-09-30T13:00:00Z" // not received yet
  }

  def "getFulfillmentDeadline returns correct deadline"() {
    given:
    def payment = Mock(SavingFundPayment) {
      getReceivedBefore() >> (receivedStr ? Instant.parse(receivedStr) : null)
    }
    publicHolidays.nextWorkingDay(_ as LocalDate) >> { LocalDate date ->
      // simple mock: skip weekends
      if (date.dayOfWeek.value >= 6) {
        return date.plusDays(8 - date.dayOfWeek.value)
      }
      return date.plusDays(1)
    }

    expect:
    service.getFulfillmentDeadline(payment) == Instant.parse(expectedDeadline)

    where:
    receivedStr                    | expectedDeadline
    "2025-09-29T12:00:00Z"         | "2025-10-01T13:00:00Z" // received on Monday → Wednesday
    "2025-09-30T12:00:00Z"         | "2025-10-02T13:00:00Z" // received on Tuesday → Thursday
    null                           | "2025-10-02T13:00:00Z" // not received → today +2 working days
  }
}
