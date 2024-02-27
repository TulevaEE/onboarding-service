package ee.tuleva.onboarding.paymentrate

import spock.lang.Specification

class PaymentRatesSpec extends Specification {
  def "has increased"() {
    when:
    PaymentRates paymentRates = new PaymentRates(current, pending)
    then:
    paymentRates.hasIncreased() == hasIncreased
    where:
    current | pending || hasIncreased
    null    | null    || false
    2       | null    || false
    4       | null    || true
    null    | 2       || false
    null    | 4       || true
  }
}
