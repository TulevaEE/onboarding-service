package ee.tuleva.onboarding.payment.recurring


import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.payment.recurring.PaymentDateProvider.format
import static ee.tuleva.onboarding.time.TestClockHolder.clock

class PaymentDateProviderSpec extends Specification {

  PaymentDateProvider paymentDateProvider = new PaymentDateProvider(clock)

  def "chooses the 10th day of month for recurring payment"() {
    when:
    def date = paymentDateProvider.tenthDayOfMonth(now)
    def formattedDate = format(date)
    then:
    formattedDate == expectedPaymentDate
    where:
    now                       | expectedPaymentDate
    LocalDate.of(2022, 3, 9)  | "10.03.2022"
    LocalDate.of(2022, 3, 10) | "10.03.2022"
    LocalDate.of(2022, 3, 11) | "10.04.2022"
  }

}
