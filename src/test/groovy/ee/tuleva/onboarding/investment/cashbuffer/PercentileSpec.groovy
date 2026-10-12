package ee.tuleva.onboarding.investment.cashbuffer

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.investment.cashbuffer.Percentile.interpolatedBetweenClosestRanks

class PercentileSpec extends Specification {

  @Unroll
  def "the #fraction percentile of #values is #expected"() {
    expect:
    interpolatedBetweenClosestRanks(values, fraction) == expected

    where:
    values                                     | fraction || expected
    [72000.00, 0.00, 30000.00, 15000.00]       | 0.95     || 65700.00
    [900000.00, 800000.00, 0.00, 1000000.00]   | 0.20     || 480000.00
    [30000.00, 72000.00, 0.00]                 | 0.95     || 67800.00
    [42.00]                                    | 0.95     || 42.00
    [10.00, 20.00]                             | 1.0      || 20.00
    [10.00, 20.00]                             | 0.0      || 10.00
    [10.00, 20.00, 30.00]                      | 0.5      || 20.00
    [0.00, 0.00, 0.00]                         | 0.95     || 0.00
  }

  @Unroll
  def "refuses #reason"() {
    when:
    interpolatedBetweenClosestRanks(values, fraction)

    then:
    thrown(IllegalArgumentException)

    where:
    reason                    | values    | fraction
    "an empty series"         | []        | 0.95
    "a fraction above one"    | [1.00]    | 1.01
    "a negative fraction"     | [1.00]    | -0.01
  }
}
