package ee.tuleva.onboarding.mandate.details

import spock.lang.Specification

import static ee.tuleva.onboarding.applicationtype.ApplicationType.PAYMENT_RATE
import static ee.tuleva.onboarding.mandate.details.PaymentRateChangeMandateDetails.PaymentRate

class PaymentRateChangeMandateDetailsSpec extends Specification {

  def "getApplicationType is PAYMENT_RATE"() {
    expect:
    new PaymentRateChangeMandateDetails(PaymentRate.SIX).getApplicationType() == PAYMENT_RATE
  }

  def "PaymentRate.fromValue finds the discrete rate matching the numeric value"() {
    expect:
    PaymentRate.fromValue(new BigDecimal("2")) == PaymentRate.TWO
    PaymentRate.fromValue(new BigDecimal("4")) == PaymentRate.FOUR
    PaymentRate.fromValue(new BigDecimal("6")) == PaymentRate.SIX
    PaymentRate.fromValue(new BigDecimal("2.0")) == PaymentRate.TWO
  }

  def "PaymentRate.fromValue throws for a value with no discrete rate"() {
    when:
    PaymentRate.fromValue(new BigDecimal("3"))

    then:
    thrown(IllegalArgumentException)
  }
}
