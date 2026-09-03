package ee.tuleva.onboarding.investment.check.fee

import ee.tuleva.onboarding.tulevafund.TulevaFund
import spock.lang.Specification
import spock.lang.Unroll

class FeeCashIngestionCoverageSpec extends Specification {

  def coverage = new FeeCashIngestionCoverage()

  @Unroll
  def "#fund cash coverage is #covered"() {
    expect:
    coverage.coversFund(fund) == covered

    where:
    fund                | covered
    TulevaFund.TKF100   | true
    TulevaFund.TUK75    | false
    TulevaFund.TUK00    | false
    TulevaFund.TUV100   | false
  }

  def "a new fund is not silently assumed to have cash ingestion"() {
    expect:
    TulevaFund.values().findAll { coverage.coversFund(it) } == [TulevaFund.TKF100]
  }
}
