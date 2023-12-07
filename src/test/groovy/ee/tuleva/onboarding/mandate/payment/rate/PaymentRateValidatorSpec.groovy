package ee.tuleva.onboarding.mandate.payment.rate

import spock.lang.Specification
import spock.lang.Unroll

import javax.validation.Validation
import javax.validation.Validator

class PaymentRateValidatorSpec extends Specification {

  private Validator validator

  def setup() {
    validator = Validation.buildDefaultValidatorFactory().validator
  }

  @Unroll
  def "test payment rate with value #rate are #isValid"() {
    given:
    BigDecimal testRate = rate != null ? new BigDecimal(rate) : null

    when:
    def violations = validator.validateValue(PaymentRateValidator, 'paymentRate', testRate)

    then:
    if (isValid) {
      violations.isEmpty()
    } else {
      !violations.isEmpty()
    }

    where:
    rate    | isValid
    "2.0"   | true
    "2.1"   | false
    "4.0"   | true
    "6.0"   | true
    "3.0"   | false
    "5.0"   | false
    "7.0"   | false
    null    | true
  }
}
