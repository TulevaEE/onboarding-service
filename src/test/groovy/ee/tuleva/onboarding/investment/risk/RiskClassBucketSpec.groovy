package ee.tuleva.onboarding.investment.risk

import spock.lang.Specification
import spock.lang.Unroll

class RiskClassBucketSpec extends Specification {

  @Unroll
  def "PRIIPs MRM class for VEV #vev is #expected"() {
    expect:
    RiskClassBucket.mrmClass(vev) == expected
    where:
    vev      || expected
    0.0      || 1
    0.00499  || 1
    0.005    || 2
    0.0499   || 2
    0.05     || 3
    0.1199   || 3
    0.12     || 4
    0.1999   || 4
    0.20     || 5
    0.2999   || 5
    0.30     || 6
    0.7999   || 6
    0.80     || 7
    1.50     || 7
  }

  @Unroll
  def "CESR SRRI class for annualised volatility #vol is #expected"() {
    expect:
    RiskClassBucket.srriClass(vol) == expected
    where:
    vol      || expected
    0.0      || 1
    0.00499  || 1
    0.005    || 2
    0.0199   || 2
    0.02     || 3
    0.0499   || 3
    0.05     || 4
    0.0999   || 4
    0.10     || 5
    0.1499   || 5
    0.15     || 6
    0.2499   || 6
    0.25     || 7
    0.60     || 7
  }

  @Unroll
  def "MRM class #riskClass upper bound is #expected"() {
    expect:
    RiskClassBucket.range(RiskIndicatorType.SRI, riskClass).upperExclusive() == expected
    where:
    riskClass || expected
    6         || 0.80
    7         || null
  }

  @Unroll
  def "class #riskClass measures #value against its nearer bound: #expected"() {
    expect:
    RiskClassBucket.distanceToNearestBound(RiskIndicatorType.SRRI, riskClass, value) == expected
    where:
    riskClass | value  || expected
    1         | 0.001  || 0.004
    7         | 0.30   || 0.05
    4         | 0.06   || 0.01
    4         | 0.09   || 0.01
  }
}
