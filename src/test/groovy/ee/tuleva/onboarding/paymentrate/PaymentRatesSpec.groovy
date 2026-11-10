package ee.tuleva.onboarding.paymentrate

import spock.lang.Specification

class PaymentRatesSpec extends Specification {
  def "a pending rate above the current one is a raise, below it a decrease, and equal to it neither"() {
    when:
    PaymentRates paymentRates = new PaymentRates(current, pending)
    then:
    paymentRates.hasPendingRaise() == raise
    paymentRates.hasPendingDecrease() == decrease
    where:
    current | pending || raise | decrease
    null    | null    || false | false
    null    | 4       || false | false
    2       | null    || false | false

    2       | 2       || false | false
    2       | 4       || true  | false
    2       | 6       || true  | false

    4       | 2       || false | true
    4       | 4       || false | false
    4       | 6       || true  | false

    6       | 2       || false | true
    6       | 4       || false | true
    6       | 6       || false | false
  }

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

  def "the pending rate applies over the current one"() {
    when:
    PaymentRates paymentRates = new PaymentRates(current, pending)
    then:
    paymentRates.rateThatApplies() == rateThatApplies
    where:
    current | pending || rateThatApplies
    null    | null    || null
    null    | 4       || 4
    2       | null    || 2
    6       | null    || 6
    2       | 6       || 6
    6       | 4       || 4
  }
}
