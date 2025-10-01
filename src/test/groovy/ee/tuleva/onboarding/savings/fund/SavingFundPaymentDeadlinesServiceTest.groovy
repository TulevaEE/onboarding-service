package ee.tuleva.onboarding.savings.fund

import ee.tuleva.onboarding.deadline.PublicHolidays
import spock.lang.Specification
import spock.lang.Unroll

import java.time.*

class SavingFundPaymentDeadlinesServiceTest extends Specification {

  def publicHolidays = Mock(PublicHolidays)
  def timeZone = ZoneId.of("Europe/Tallinn")
  def clock = Mock(Clock)

  def service = new SavingFundPaymentDeadlinesService(publicHolidays, clock)

  def setup() {
    clock.getZone() >> timeZone
  }

  @Unroll
  def "getCancellationDeadline when payment created on working day #paymentTime should return deadline #expectedDeadline"() {
    given:
    def paymentDate = LocalDate.of(2025, 9, 30) // Tuesday
    def paymentCreatedAt = ZonedDateTime.of(paymentDate, paymentTime, timeZone).toInstant()
    def payment = SavingFundPayment.builder()
        .createdAt(paymentCreatedAt)
        .build()

    and: "Tuesday is a working day"
    publicHolidays.nextWorkingDay(paymentDate.minusDays(1)) >> paymentDate

    and: "Next working day after Tuesday is Wednesday"
    publicHolidays.nextWorkingDay(paymentDate) >> paymentDate.plusDays(1)

    when:
    def result = service.getCancellationDeadline(payment)

    then:
    def expectedInstant = ZonedDateTime.of(expectedDate, LocalTime.of(15, 59), timeZone).toInstant()
    result == expectedInstant

    where:
    paymentTime                || expectedDate                           || expectedDeadline
    LocalTime.of(9, 0)        || LocalDate.of(2025, 9, 30)            || "same day at 15:59"
    LocalTime.of(15, 58)      || LocalDate.of(2025, 9, 30)            || "same day at 15:59"
    LocalTime.of(15, 59)      || LocalDate.of(2025, 10, 1)            || "next working day at 15:59"
    LocalTime.of(16, 0)       || LocalDate.of(2025, 10, 1)            || "next working day at 15:59"
    LocalTime.of(23, 59)      || LocalDate.of(2025, 10, 1)            || "next working day at 15:59"
  }

  def "getCancellationDeadline when payment created on weekend should return next working day"() {
    given:
    def saturdayDate = LocalDate.of(2025, 10, 4) // Saturday
    def paymentCreatedAt = ZonedDateTime.of(saturdayDate, LocalTime.of(10, 0), timeZone).toInstant()
    def payment = SavingFundPayment.builder()
        .createdAt(paymentCreatedAt)
        .build()

    and: "Saturday is not a working day, next working day from Friday is Monday"
    publicHolidays.nextWorkingDay(saturdayDate.minusDays(1)) >> LocalDate.of(2025, 10, 6) // Monday

    and: "Next working day after Saturday is Monday"
    publicHolidays.nextWorkingDay(saturdayDate) >> LocalDate.of(2025, 10, 6) // Monday

    when:
    def result = service.getCancellationDeadline(payment)

    then:
    def expectedInstant = ZonedDateTime.of(LocalDate.of(2025, 10, 6), LocalTime.of(15, 59), timeZone).toInstant()
    result == expectedInstant
  }

  def "getCancellationDeadline when payment created on holiday should return next working day"() {
    given:
    def holidayDate = LocalDate.of(2025, 12, 25) // Christmas Day (assumed holiday)
    def paymentCreatedAt = ZonedDateTime.of(holidayDate, LocalTime.of(10, 0), timeZone).toInstant()
    def payment = SavingFundPayment.builder()
        .createdAt(paymentCreatedAt)
        .build()

    and: "Christmas is not a working day, next working day from Dec 24 is Dec 26"
    publicHolidays.nextWorkingDay(holidayDate.minusDays(1)) >> LocalDate.of(2025, 12, 26)

    and: "Next working day after Christmas is Dec 26"
    publicHolidays.nextWorkingDay(holidayDate) >> LocalDate.of(2025, 12, 26)

    when:
    def result = service.getCancellationDeadline(payment)

    then:
    def expectedInstant = ZonedDateTime.of(LocalDate.of(2025, 12, 26), LocalTime.of(15, 59), timeZone).toInstant()
    result == expectedInstant
  }

  def "getCancellationDeadline when payment created on Friday after 15:59 should return next Monday"() {
    given:
    def fridayDate = LocalDate.of(2025, 10, 3) // Friday
    def paymentCreatedAt = ZonedDateTime.of(fridayDate, LocalTime.of(16, 30), timeZone).toInstant()
    def payment = SavingFundPayment.builder()
        .createdAt(paymentCreatedAt)
        .build()

    and: "Friday is a working day"
    publicHolidays.nextWorkingDay(fridayDate.minusDays(1)) >> fridayDate

    and: "Next working day after Friday is Monday"
    publicHolidays.nextWorkingDay(fridayDate) >> LocalDate.of(2025, 10, 6) // Monday

    when:
    def result = service.getCancellationDeadline(payment)

    then:
    def expectedInstant = ZonedDateTime.of(LocalDate.of(2025, 10, 6), LocalTime.of(15, 59), timeZone).toInstant()
    result == expectedInstant
  }

  def "getCancellationDeadline handles timezone conversion correctly"() {
    given:
    def utcZone = ZoneId.of("UTC")
    def utcClock = Mock(Clock)
    utcClock.getZone() >> utcZone
    def utcService = new SavingFundPaymentDeadlinesService(publicHolidays, utcClock)

    def paymentDate = LocalDate.of(2025, 9, 30)
    def paymentCreatedAt = ZonedDateTime.of(paymentDate, LocalTime.of(10, 0), utcZone).toInstant()
    def payment = SavingFundPayment.builder()
        .createdAt(paymentCreatedAt)
        .build()

    and: "Working day setup"
    publicHolidays.nextWorkingDay(paymentDate.minusDays(1)) >> paymentDate
    publicHolidays.nextWorkingDay(paymentDate) >> paymentDate.plusDays(1)

    when:
    def result = utcService.getCancellationDeadline(payment)

    then:
    def expectedInstant = ZonedDateTime.of(paymentDate, LocalTime.of(15, 59), utcZone).toInstant()
    result == expectedInstant
  }

  def "getCancellationDeadline when working day spans holiday weekend"() {
    given:
    def thursdayDate = LocalDate.of(2025, 4, 17) // Thursday before Easter
    def paymentCreatedAt = ZonedDateTime.of(thursdayDate, LocalTime.of(16, 0), timeZone).toInstant()
    def payment = SavingFundPayment.builder()
        .createdAt(paymentCreatedAt)
        .build()

    and: "Thursday is a working day"
    publicHolidays.nextWorkingDay(thursdayDate.minusDays(1)) >> thursdayDate

    and: "Next working day after Thursday (skipping Good Friday, Easter weekend) is Tuesday"
    publicHolidays.nextWorkingDay(thursdayDate) >> LocalDate.of(2025, 4, 22) // Tuesday after Easter

    when:
    def result = service.getCancellationDeadline(payment)

    then:
    def expectedInstant = ZonedDateTime.of(LocalDate.of(2025, 4, 22), LocalTime.of(15, 59), timeZone).toInstant()
    result == expectedInstant
  }
}
