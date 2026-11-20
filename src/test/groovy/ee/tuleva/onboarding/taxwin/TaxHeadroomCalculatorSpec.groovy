package ee.tuleva.onboarding.taxwin

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.taxwin.LimitCertainty.CERTAIN
import static ee.tuleva.onboarding.taxwin.LimitCertainty.ESTIMATED
import static ee.tuleva.onboarding.taxwin.PaymentSuggestion.RECURRING
import static ee.tuleva.onboarding.taxwin.PaymentSuggestion.SINGLE

class TaxHeadroomCalculatorSpec extends Specification {

  def calculator = new TaxHeadroomCalculator()

  @Unroll
  def "#knownIncome declared and #expectedIncome expected with #paid paid leaves #remaining of a #limit limit"() {
    expect:
    calculator.calculate(knownIncome, knownDeductions, expectedIncome, paid, monthsLeft) ==
        new HeadroomFigures(limit, limitCertainty, remaining, refund, monthlyPayment, paymentSuggestion)

    where:
    knownIncome | knownDeductions | expectedIncome | paid    | monthsLeft || limit | limitCertainty | remaining | refund | monthlyPayment | paymentSuggestion
    24000.00    | 864.00          | 0.00           | 0.00    | 12         || 3600  | ESTIMATED      | 3600      | 792    | 300            | RECURRING
    9600.00     | 345.60          | 0.00           | 0.00    | 12         || 854   | ESTIMATED      | 854       | 187    | 71             | RECURRING
    7200.00     | 259.20          | 0.00           | 0.00    | 12         || 0     | ESTIMATED      | 0         | 0      | 0              | RECURRING
    50000.00    | 1800.00         | 0.00           | 0.00    | 12         || 6000  | CERTAIN        | 6000      | 1320   | 500            | RECURRING
    25000.00    | 900.00          | 5000.00        | 3260.00 | 2          || 4500  | ESTIMATED      | 1240      | 272    | 375            | SINGLE
    25000.00    | 900.00          | 5000.00        | 5000.00 | 2          || 4500  | ESTIMATED      | 0         | 0      | 0              | SINGLE
    25000.00    | 900.00          | 15000.00       | 3260.00 | 2          || 6000  | ESTIMATED      | 2740      | 602    | 500            | SINGLE
    0.00        | 0.00            | 30000.00       | 0.00    | 12         || 4500  | ESTIMATED      | 4500      | 990    | 375            | RECURRING
    41000.00    | 1476.00         | 0.00           | 0.00    | 3          || 6000  | CERTAIN        | 6000      | 1320   | 500            | RECURRING
  }

  def "estimated months deduct unemployment insurance and II pillar at 3.6 percent"() {
    expect:
    calculator.calculate(0.00, 0.00, 9600.00, 0.00, 12).limit() == 854
  }

  def "declared months deduct what was actually withheld"() {
    expect:
    calculator.calculate(9600.00, 0.00, 0.00, 0.00, 12).limit() == 1200
  }

  def "a payment above the limit leaves nothing rather than a negative amount"() {
    expect:
    calculator.calculate(24000.00, 864.00, 0.00, 5000.00, 1).remaining() == 0
  }
}
