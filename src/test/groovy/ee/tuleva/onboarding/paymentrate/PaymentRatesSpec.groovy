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

  def "can increase"() {
    when:
    PaymentRates paymentRates = new PaymentRates(current, pending)
    then:
    paymentRates.canIncrease() == canIncrease
    where:
    current | pending || canIncrease
    null    | null    || true
    null    | 2       || true
    null    | 4       || true
    null    | 6       || false

    2       | null    || true
    2       | 2       || true
    2       | 4       || true
    2       | 6       || false

    4       | null    || true
    4       | 2       || true
    4       | 4       || true
    4       | 6       || false

    6       | null    || false
    6       | 2       || true
    6       | 4       || true
    6       | 6       || false
  }
}
